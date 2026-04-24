package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.project.NewNotebookDialog
import com.intellij.jupyter.ui.test.util.project.withNewNotebookDialog
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookWelcomeScreenTest : KotlinNotebooksBaseTest(stopAndCloseNotebooksAfterTest = false) {

  @Test
  fun `create scratch notebook`() = welcomeScreenBaseTest {
    scratchTypeButton.click()
    createButton.click()
  }

  @Test
  fun `create notebook in folder`() = welcomeScreenBaseTest {
    standardTypeButton.click()
    createButton.click()
  }

  private fun welcomeScreenBaseTest(testBody: com.intellij.jupyter.ui.test.util.project.NewNotebookDialog.() -> Unit) = withDriver {
    welcomeScreen {
      step("Create new scratch notebook") {
        withNewNotebookDialog {
          testBody()
        }
      }
    }
    step("Do basic notebook check") {
      ideFrame {
        try {
          notebookEditor().waitFound(30.seconds).run {
            addCodeCell("2 + 2")
            runAllCellsAndWaitExecuted(2.minutes)
            notebookCellOutputs.apply {
              shouldBeSingleton()
              val allTexts = single().getAllTexts()
              allTexts.shouldBeSingleton()
              allTexts.single().text shouldBe "4"
            }
          }
        } finally {
          closeProject()
        }
      }
    }
  }
}
