// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.base

import com.intellij.driver.client.Driver
import com.intellij.driver.client.utility
import com.intellij.driver.sdk.SetupProjectSdkUtil
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDERunContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Base class for UI testing infrastructure, originally extracted from [com.intellij.driver.tests.kotlin.KotlinBaseUiTest].
 *
 * This class provides core functionality for UI testing including:
 * - IDE startup and teardown
 * - Driver management for UI interaction
 * - Critical test failure tracking
 * - Notebook editor testing support
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseUITest(val testRunTimeout: Duration = 25.minutes) {
  protected lateinit var bgRun: BackgroundRun
  protected lateinit var ideContext: IDERunContext
  protected lateinit var testContext: IDETestContext

  /**
   * Timeout for waiting for indexing to complete during IDE startup.
   * Set to `null` to skip waiting (e.g., when [afterStartIDEAction] handles it with a longer timeout).
   */
  protected open val pauseOnIndexing: Duration? = 5.minutes

  /**
   * When `true`, records the entire driver session (IDE startup to shutdown)
   * in addition to per-test recordings from [IDEScreenRecordingExtension].
   */
  protected open val enableDriverScreenRecording: Boolean = false

  @RegisterExtension
  @JvmField
  val screenRecording = IDEScreenRecordingExtension { if (::ideContext.isInitialized) ideContext else null }

  protected abstract fun configureIDETestContext(): IDETestContext

  protected open fun beforeStartIDEAction() {}
  protected open fun afterStartIDEAction() {}

  protected open fun beforeCloseIDEAction() {}
  protected open fun afterCloseIDEAction() {}

  /**
   * Configuration of VM parameters for the remote instance.
   * Use `+=` to add patches in subclasses.
   */
  val additionalVMOptionPatches: MutableList<VMOptions.() -> Unit> = mutableListOf()

  /**
   * Tracks the state of whether a critical problem occurred during execution.
   *
   * When a critical test fails, this variable is set to `true` to indicate that subsequent tests
   * should be skipped.
   */
  private var criticalProblemOccurred = false

  /**
   * Checks if the IDE driver is available and connected.
   *
   * Returns `false` if:
   * - The IDE was never started (e.g., initialization failed before [bgRun] was set)
   * - The driver connection was lost
   *
   * Use this as a guard before any driver interactions.
   */
  fun isDriverConnected() = ::bgRun.isInitialized && bgRun.driver.isConnected

  /**
   * Use this to access IDE ui and api via Driver in tests
   */
  fun withDriver(block: Driver.() -> Unit) = if (isDriverConnected()) {
    bgRun.driver.withContext {
      block()
    }
  }
  else {
    if (!criticalProblemOccurred) {
      criticalProblemOccurred = true
      error("IDE is not connected")
    }
    else Unit
  }

  /**
   * Suppose to wrap code a test doing vital stuff for other tests (e.g. 'Open project')
   * Executes a block of code and ensures that any uncaught exception within the block
   * sets the `criticalTestFailed` flag to `true`.
   *
   * @param block The block of code to execute. Any uncaught exception in this block will
   *              mark the test as failed and propagate the exception.
   */
  protected fun critical(block: () -> Unit) {
    try {
      block()
    }
    catch (e: Throwable) {
      criticalProblemOccurred = true
      throw e
    }
  }

  @BeforeEach
  fun `skip other tests if critical test failed`() = Assumptions.assumeFalse(criticalProblemOccurred)

  @AfterEach
  fun checkIdeState() {
    if (criticalProblemOccurred.not() && isDriverConnected().not()) {
      criticalProblemOccurred = true
      error("IDE is not connected")
    }
  }

  /**
   * Executes a test block within the context of the IDE frame.
   */
  fun ideFrameTest(testBody: IdeaFrameUI.() -> Unit) = withDriver {
    ideFrame {
      testBody()
    }
  }

  /**
   * Starts the IDE instance with a predefined configuration and test environment setup.
   */
  @BeforeAll
  fun startIde() {
    testContext = configureIDETestContext()
    beforeStartIDEAction()
    bgRun = testContext.applyVMOptionsPatch {
      addSystemProperty("expose.ui.hierarchy.url", "true")
      additionalVMOptionPatches.forEach { it() }
    }.runIdeWithDriver(runTimeout = testRunTimeout, pauseOnIndexing = pauseOnIndexing) {
      ideContext = this
      if (enableDriverScreenRecording) withScreenRecording()
    }.also {
      it.driver.withContext {
        waitFor("Application is loaded", timeout = 2.minutes) { utility<SetupProjectSdkUtil>().isApplicationLoaded() }
      }
    }
    afterStartIDEAction()
  }

  /**
   * Stops the running instance of the IDE
   */
  @AfterAll
  fun stopIde() {
    if (isDriverConnected()) {
      beforeCloseIDEAction()
      bgRun.closeIdeAndWait()
      afterCloseIDEAction()
    }
  }
}

