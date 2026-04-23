// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.tests.kotlin.notebooks.base

import com.intellij.driver.client.utility
import com.intellij.driver.sdk.SetupProjectSdkUtil
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.utils.getProjectPath
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import com.intellij.ide.starter.junit5.newContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.report.AllureHelper.attachFile
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for UI tests for Kotlin related features in IDE.
 * It provides configuration for handling test failures, managing IDE processes, and storing test-related data.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KotlinBaseUiTest.SaveDiagnosticsOnFailure::class)
abstract class KotlinBaseUiTest(
  val testProject: ProjectInfoSpec = NoProject,
  val ideInfo: IdeInfo = IdeInfo.IdeaUltimate,
  testRunTimeout: Duration = 15.minutes,
) : BaseUITest(testRunTimeout = testRunTimeout) {

  protected var additionalPlugins: List<String> = emptyList()

  override fun configureIDETestContext(): IDETestContext {
    val suiteName = this::class.java.simpleName
    logOutput("creating new context for test $suiteName")
    return Starter.newContext(
      ideInfo,
      suiteName,
      configure = {
        project = testProject
        systemProperties = mapOf("decompiler.legal.notice.accepted" to "true")
      }
    ).also {
      logOutput("installing additional plugins:")
      additionalPlugins.forEach { pluginId ->
        //it.pluginConfigurator.installPlugin(pluginId)
        logOutput("Plugin '$pluginId' was installed")
      }
    }
  }

  override fun afterStartIDEAction() {
    bgRun.driver.withContext {
      waitFor("Application is loaded", timeout = 15.seconds) {
        utility<SetupProjectSdkUtil>().isApplicationLoaded()
      }
    }
  }

  /**
   * Adds the active test project as a report attachment.
   */
  override fun beforeCloseIDEAction() {
    fun zipDirectoryToInputStream(directoryPath: String): InputStream {
      val byteArrayOutputStream = ByteArrayOutputStream()
      ZipOutputStream(byteArrayOutputStream).use { zipOut ->
        val sourceDirPath = Paths.get(directoryPath)
        Files.walk(sourceDirPath).forEach { path ->
          val zipEntry = ZipEntry(sourceDirPath.relativize(path).toString() + if (Files.isDirectory(path)) "/" else "")
          zipOut.putNextEntry(zipEntry)
          if (!Files.isDirectory(path)) {
            Files.newInputStream(path).use { input -> input.copyTo(zipOut) }
          }
          zipOut.closeEntry()
        }
      }
      return ByteArrayInputStream(byteArrayOutputStream.toByteArray())
    }

    bgRun.driver.getProjectPath()?.let { projectPath ->
      zipDirectoryToInputStream(projectPath).use { inputStream ->
        ideContext.logsDir.resolve("project_backup.zip")
          .outputStream()
          .use { outputStream ->
            inputStream.copyTo(outputStream)
          }
      }
    }
  }

  protected fun attachScreenshot(folderName: String, name: String = "screenshot") {
    bgRun.driver.withContext {
      takeScreenshot(folderName)?.run {
        attachFile(name, Path.of(this))
      }
    }
  }

  /**
   * Callback to capture a screenshot when a test fails.
   */
  class SaveDiagnosticsOnFailure : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
      val test = context.requiredTestInstance as KotlinBaseUiTest
      if (context.executionException.isPresent && test.isDriverConnected()) {
        test.bgRun.driver.withContext {
          takeScreenshot(context.testMethod.get().name)?.run {
            attachFile("after test screenshot", Path.of(this))
          }
          ui.robotProvider.saveHierarchy(
            test.ideContext.logsDir.toString(),
            "${context.testMethod.get().name.replace(" ", "-")}-hierarchy-on-fail.html"
          )
        }
      }
    }
  }
}
