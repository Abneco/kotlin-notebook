package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.editor.intentionList
import com.intellij.driver.sdk.ui.components.common.editor.openIntentions
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.seconds

class KotlinNotebooksRefactoringTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world") {

  @Test
  fun `import quick fix test`() = doIntentionTest(
    cellCode = "val f = File(\"test.txt\")",
    intentionName = "Import class 'File'",
    checkResult = { text.contains("import java.io.File") },
    waitForConditionDescription = "import java.io.File to be added"
  )

  @Test
  fun `specify type explicitly test`() = doIntentionTest(
    cellCode = """
          class Test
          val a = listOf(Pair(Test(), ""))
        """.trimIndent(),
    moveCaretTo = "val a",
    intentionName = "Specify type explicitly",
    checkResult = { text.contains("val a: List<Pair<Test, String>>") },
    waitForConditionDescription = "type to be specified"
  )

  @Test
  fun `convert lambda to anonymous function test`() = doIntentionTest(
    cellCode = """
          fun foo(f: (Int) -> Int) = f(1)
          val x = foo { it * it }
        """.trimIndent(),
    moveCaretTo = "{",
    intentionName = "Convert to anonymous function",
    checkResult = { text.contains("foo(fun(it: Int): Int") },
    waitForConditionDescription = "lambda to be converted to anonymous function"
  )

  @Test
  fun `create extension function test`() = doIntentionTest(
    cellCode = """
      class A
      A().foo()
    """.trimIndent(),
    moveCaretTo = "foo",
    intentionName = "Create extension fu", // intention name is trimmed
    intentionNameTrimmed = true,
    checkResult = { text.contains("fun A.foo()") && !text.contains("private fun A.foo()") },
    waitForConditionDescription = "extension function to be created without private modifier"
  )

  @Test
  fun `java to kotlin conversion test`() = withDriver {
    withNotebookEditor {
      val javaCode = "public static void main(String[] args) {}"

      step("Add java code") {
        pasteToCurrentCell(javaCode)
      }

      step("Confirm conversion in dialog") {
        ui.dialog(title = "Convert Code From Java") {
          pressButton("Yes")
        }
      }

      step("Check result") {
        waitFor("Code to be converted to kotlin", 10.seconds) {
          text.contains("fun main() {}") &&
          !text.contains(javaCode)
        }
      }
    }
  }

  private fun doIntentionTest(
    cellCode: String,
    intentionName: String,
    intentionNameTrimmed: Boolean = false,
    moveCaretTo: String? = null,
    waitForConditionDescription: String,
    checkResult: NotebookEditorUiComponent.() -> Boolean,
  ) = withDriver {
    withNotebookEditor {
      step("Add code") {
        addCodeCell(cellCode)
      }

      step("Wait for highlighting") {
        waitForHighlighting()
      }

      step("Invoke intention: $intentionName") {
        moveCaretTo?.let { moveCaretToText(it) }
        openIntentions()
        ui.ideFrame().intentionList().clickItem(intentionName,   !intentionNameTrimmed)
      }

      step("Check result") {
        waitFor(waitForConditionDescription, 10.seconds) {
          checkResult()
        }
      }
    }
  }

  @BeforeEach
  fun setUp(testInfo: TestInfo) = withDriver {
    createNewNotebook(testInfo)
  }
}