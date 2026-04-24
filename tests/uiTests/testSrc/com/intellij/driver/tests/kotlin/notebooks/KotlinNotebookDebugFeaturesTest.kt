package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.notebooks.CellSelector
import com.intellij.driver.sdk.ui.components.notebooks.LastCell
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting
import com.intellij.driver.sdk.ui.components.notebooks.withKotlinNotebookSettingsDialog
import com.intellij.driver.sdk.ui.components.notebooks.withKotlinNotebookToolWindow
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.driver.tests.kotlin.notebooks.utils.setNotebookDebugFeatures
import com.intellij.driver.tests.kotlin.notebooks.utils.waitForKernelRestartNotification
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.waitCellsAreExecuted
import com.intellij.jupyter.ui.test.util.utils.debugToolWindow
import com.intellij.jupyter.ui.test.util.utils.placeBreakpointInFile
import com.intellij.jupyter.ui.test.util.utils.removeAllBreakpoints
import com.intellij.jupyter.ui.test.util.utils.resumeProgram
import com.intellij.debugger.ui.test.util.debugger
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.waitCellsAreExecuted
import com.intellij.jupyter.ui.test.util.utils.debugToolWindow
import com.intellij.jupyter.ui.test.util.utils.placeBreakpointInFile
import com.intellij.jupyter.ui.test.util.utils.removeAllBreakpoints
import com.intellij.jupyter.ui.test.util.utils.resumeProgram
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookDebugFeaturesTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world-kotlin") {
  private val greetBreakpointLine = 12

  private val targetFileLocation = "src/com/jonnyzzz/Main.kt"

  private fun IdeaFrameUI.enableVariablesViewInSettings() {
    step("Enable variables view") {
      withKotlinNotebookSettingsDialog {
        focusOnVariablesCheckBox.click()
      }
    }
  }

  @Test
  fun variablesViewClearedAfterRestart() = ideFrameTest {
    enableVariablesViewInSettings()

    step("Run cell and wait executed") {
      notebookEditor {
        addKotlinCell("""
            val x = 2
            val l = listOf(1, 2, 3)
          """.trimIndent())
        runCellAndWaitExecuted(timeout = 1.minutes)
      }
    }

    step("Check variables view updated") {
      assertVariablesViewHasText("x = 2", ensureOpened = true)
    }

    step("Restart session") {
      notebookEditor {
        restartKernel()
      }
      waitForKernelRestartNotification()
      assertVariablesViewIsEmpty()
    }
  }

  @Test
  fun `check execution without breakpoints`() = ideFrameTest {
    notebookEditor {
      addKotlinCell("val x = 42\nprintln(x)")

      step("Run first cell via debug action without breakpoints") {
        runDebugCell(false)
        waitCellsAreExecuted(expectedExecutionCount = 1)
      }
    }
  }

  @Test
  fun `check project breakpoints are hit`(testInfo: TestInfo) = ideFrameTest {
    step("Set breakpoint in project class") {
      placeBreakpointInFile(targetFileLocation, greetBreakpointLine)
      openEditorTabByName(testInfo.displayName)
    }

    step("Run cell - breakpoint should be hit") {
      notebookEditor {
        waitForHighlighting()
        addKotlinCell("""
          import com.jonnyzzz.Main
          Main.greet("Kotlin")
        """.trimIndent())
      }
      runDebugCell(true, timeout = 1.minutes)
      editorTabs {
        tab("Main.kt", fullMatch = false).waitFound()
      }
    }

    step("Resume and verify return to notebook editor") {
      driver.resumeProgram()
      notebookEditor().waitFound(30.seconds)
    }
  }

  fun IdeaFrameUI.assertVariablesViewHasText(text: String, ensureOpened: Boolean = false) {
    withKotlinNotebookToolWindow {
      if (ensureOpened) {
        openVariablesTab()
      }
      waitForVariablesViewText(text)
    }
  }

  fun IdeaFrameUI.assertVariablesViewIsEmpty() {
    assertVariablesViewHasText("No variables are defined yet")
  }

  /**
   * Invokes debug action inside a [cellSelector] cell.
   * If notebook settings opt-in for regular execution with no breakpoints present,
   * expects debug UI to be not opened.
   */
  fun IdeaFrameUI.runDebugCell(expectDebugToolWindow: Boolean, timeout: Duration = 30.seconds, cellSelector: CellSelector = LastCell) = step("Start debug in cell: $cellSelector") {
    notebookEditor {
      clickOnCell(cellSelector)
    }
    withDriver {
      invokeAction("KotlinNotebookDebugCellAction", false)
      if (expectDebugToolWindow) {
        debugToolWindow.waitFound(timeout)
        debugger.waitForDebugToolWindowsReady()
      } else {
        debugToolWindow.waitNotFound(timeout)
      }
    }
  }

  @AfterEach
  fun teardown() = withDriver {
    step("Check debugger session is stopped") {
      debugToolWindow {
        waitNotFound(20.seconds)
      }
    }
    step("Remove all breakpoints") {
      removeAllBreakpoints()
    }
  }

  @BeforeEach
  fun setup(testInfo: TestInfo) = withDriver {
    createNewNotebook(testInfo)
    withNotebookEditor {
      kotlinNotebookToolbar.dependenciesSelector.selectItem("hello-world-kotlin")
    }
  }

  @BeforeAll
  fun enableDebug() {
    withDriver {
      setNotebookDebugFeatures(true)
    }
  }
}