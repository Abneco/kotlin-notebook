package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.setupOrDetectSdk
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.utils.setNotebookDebugFeatures
import com.intellij.jupyter.ui.test.util.completion.checkCompletionVariantsWithReport
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import com.intellij.jupyter.ui.test.util.completion.checkCompletionVariantsWithReport
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.File
import kotlin.time.Duration.Companion.seconds


/**
 * This test checks working with dependencies on project modules within the specified project in [testProjectResourcePath].
 * [projectSourceFilePath] is the path of a file within that project that contains the code the notebook tries to use.
 * The test will edit this file and check that the user is notified about these changes.
 */
abstract class KotlinNotebooksProjectDependenciesTest(
  private val testProjectResourcePath: String,
  private val projectSourceFilePath: String,
) : KotlinNotebooksBaseTest(testProjectResourcePath) {
  private val projectFileName: String = File(projectSourceFilePath).name

  @BeforeEach
  fun setup(testInfo: TestInfo) = withDriver {
    val sdk = JdkDownloaderFacade.jdk21.toSdk()
    setupOrDetectSdk(singleProject(), sdk.sdkName, sdk.sdkType, sdk.sdkPath.toString())
    createNewNotebook(testInfo)
    withNotebookEditor {
      kotlinNotebookToolbar.dependenciesSelector.selectItem(File(testProjectResourcePath).name)
    }
  }

  @Test
  fun `completion should have the context of the current notebook module dependency`() = withDriver {
    withNotebookEditor {
      keyboard {
        typeText("import com.jonn")
        checkCompletionVariantsWithReport("jonnyzzz.", null)
        enter()
      }
    }
  }

  @Test
  fun `execution and restart notifications`() = withDriver {
    // With the debug sessions enabled, we wouldn't need to restart the kernel
    setNotebookDebugFeatures(false)

    withNotebookEditor {
      pasteToCell(FirstCell, "import com.jonnyzzz.Main\n\nMain.main(emptyArray())")
      runAllCellsAndWaitExecuted()
      should("Project code should be executed") {
        lastNotebookOutput.contains("Hello World!")
      }
    }


    step("Edit $projectFileName") {
      editProjectFile("Hello World", "2")
    }

    withNotebookEditor {
      invokeAction("SaveAll")

      waitFor("Restart kernel button icon should have a badge") {
        restartKernelButton.hasBadge
      }
      val hint = ui.ideFrame().x { byClass("HintLabel") }.waitFound()
      hint.getAllTexts { it.text == "Don't show again" }.also {
        assertTrue(it.size == 1, "There should be a single 'Don't show again' link")
      }.single().click()

      restartKernel(30.seconds)

      restartKernelButton.should { !hasBadge }

      runAllCellsAndWaitExecuted()
      should("Changed project code should be executed") {
        lastNotebookOutput.contains("Hello World2!")
      }
    }

    step("Edit $projectFileName again") {
      editProjectFile("Hello World2", "//")
    }

    withNotebookEditor {
      invokeAction("SaveAll")

      waitFor("Restart kernel button icon should have a badge again") {
        restartKernelButton.hasBadge
      }
      wait(1.seconds)
      assertTrue(ui.ideFrame().xx { byClass("HintLabel") }.list().isEmpty(), "The link should not be shown again")
    }
  }


  private fun Driver.editProjectFile(textToAppend: String, textToAdd: String) {
    openFile(projectSourceFilePath, waitForCodeAnalysis = false)
    ideFrame {
      codeEditor().apply {
        text = text.replace(textToAppend, textToAppend + textToAdd)
      }
      editorTabs {
        closeTab(projectFileName)
      }
    }
  }
}

class KotlinNotebooksProjectDependenciesJavaTest : KotlinNotebooksProjectDependenciesTest(
  testProjectResourcePath = "kotlin/notebooks/hello-world",
  projectSourceFilePath = "src/com/jonnyzzz/Main.java",
)

class KotlinNotebooksProjectDependenciesKotlinTest : KotlinNotebooksProjectDependenciesTest(
  testProjectResourcePath = "kotlin/notebooks/hello-world-kotlin",
  projectSourceFilePath = "src/com/jonnyzzz/Main.kt",
)