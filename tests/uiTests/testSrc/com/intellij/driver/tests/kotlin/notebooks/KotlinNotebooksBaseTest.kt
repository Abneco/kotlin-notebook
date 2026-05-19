package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.invokeActionWithRetries
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.NotebookType
import com.intellij.driver.sdk.ui.components.notebooks.createNewNotebook
import com.intellij.driver.sdk.ui.components.notebooks.kotlinNotebookToolWindowButton
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting
import com.intellij.driver.sdk.ui.components.notebooks.withKotlinNotebookToolWindow
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.tests.kotlin.notebooks.base.KotlinBaseUiTest
import com.intellij.driver.tests.kotlin.notebooks.utils.resolveResourceDirectory
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.ReusableLocalProjectInfo
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.openapi.diagnostic.LogLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal const val k2ModulesRegistryKey = "kotlin.k2.scripting.show.modules"

abstract class KotlinNotebooksBaseTest(
  project: ProjectInfoSpec = NoProject,
  testRunTimeout: Duration = 15.minutes,
  private val stopAndCloseNotebooksAfterTest: Boolean = true,
) : KotlinBaseUiTest(project, testRunTimeout = testRunTimeout) {
  constructor(
    testProjectResourcePath: String,
    testRunTimeout: Duration = 15.minutes,
    stopAndCloseNotebooksAfterTest: Boolean = true,
  ) : this(
    ReusableLocalProjectInfo(projectDir = resolveResourceDirectory(testProjectResourcePath)),
    testRunTimeout,
    stopAndCloseNotebooksAfterTest
  )

  init {
    AdditionalModulesForDevBuildServer.addAdditionalModules(
        "intellij.notebooks.plugin",
        "intellij.jupyter.plugin",
        "intellij.kotlin.jupyter.plugin",
    )

    additionalVMOptionPatches += {
      addSystemProperty("org.jetbrains.plugins.kotlin.jupyter.uiDriverTests", "true")
      addSystemProperty("ide.ui.non.modal.settings.window", "false")

      configureLoggers(
        LogLevel.DEBUG,
        "com.intellij.kotlin.jupyter",
        "com.intellij.jupyter",
        "org.jetbrains.kotlinx.jupyter",
      )

      configureLoggers(
        LogLevel.TRACE,
        "com.intellij.openapi.wm.impl.ToolWindowManagerImpl",
        "com.intellij.database.run.actions",
        "com.intellij.kotlin.jupyter",
        "com.intellij.platform.ijent",
        "com.intellij.platform.eel",
        "jb.focus.requests",
        "com.intellij.jupyter"
      )

      configureLoggers(
        logLevel = "separate.file",
        "com.intellij.platform.ijent",
      )
    }
  }

  fun createNewNotebook(
    testInfo: TestInfo,
    shouldWaitForHighlighting: Boolean = true
  ) = withDriver {
    createNewNotebook(testInfo.displayName, NotebookType.KOTLIN)
    hideAllToolWindows()

    if (shouldWaitForHighlighting) {
      withNotebookEditor {
        waitForHighlighting()
      }
    }
  }

  protected fun hideAllToolWindows() = withDriver {
    try {
      invokeAction("HideAllWindows")
    } catch (_: IllegalStateException) {
      // no tool windows -> action is disabled -> skip
    }
  }

  protected fun openEditorTabByName(name: String) = withDriver {
    ideFrame {
      editorTabs {
        tab(name, fullMatch = false)
          .waitFound()
          .click()
      }
    }
  }

  @AfterEach
  fun closeNotebooks() {
    if (!stopAndCloseNotebooksAfterTest) return
    withDriver {
      ideFrame {
        // open the Kotlin Notebook tool window if it exists
        val toolWindowButton = kotlinNotebookToolWindowButton ?: return@ideFrame

        toolWindowButton.open()
        withKotlinNotebookToolWindow {
          // all tabs without a kernel session can be closed (tabs with a session won't be closed by this action)
          if (!notebookTabs.list().isEmpty()) {
            try {
              invokeAction("TW.CloseAllTabs")
            } catch (_: IllegalStateException) {
              // no closeable tabs -> action is disabled -> skip
            }
          }

          // now all the tabs that are left are tabs with a running kernel session
          while (toolWindowButton.isToolWindowVisible() && notebookTabs.list().isNotEmpty()) {
            // we need to be focused on the "kernel logs" tab of our notebook to be able to call "JupyterShutdownNotebookAction"
            openKernelLogsTab()
            invokeActionWithRetries("JupyterShutdownNotebookAction")

            // as soon as that the session is stopped, we can close this tab
            invokeActionWithRetries("TW.CloseAllTabs", maxAttempts = 30, delay = 1.seconds)
          }
        }
      }

      invokeAction("CloseAllEditors")
    }
  }
}
