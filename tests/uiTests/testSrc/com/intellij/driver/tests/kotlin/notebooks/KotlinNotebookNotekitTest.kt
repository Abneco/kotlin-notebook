package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.driver.tests.kotlin.notebooks.utils.pasteKotlinToCurrentCell
import com.intellij.ide.starter.utils.withIndent
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.waitCellsAreExecuted
import com.intellij.jupyter.ui.test.util.utils.waitNotEmpty
import io.kotest.assertions.fail
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the Notekit integration API.
 *
 * Notekit provides programmatic access to manipulate Jupyter notebooks from Kotlin kernel code.
 * API documentation: https://github.com/Kotlin/kotlin-notebook-integrations/tree/master/integrations/notekit
 */
class KotlinNotebookNotekitTest : KotlinNotebooksBaseTest(
  testProjectResourcePath = "kotlin/notebooks/hello-world",
  testRunTimeout = 20.minutes,
) {

  @BeforeEach
  fun setup(testInfo: TestInfo) = withDriver {
    createNewNotebook(testInfo, shouldWaitForHighlighting = false)

    withNotebookEditor {
      step("Import notekit") {
        { notebookCellEditors }.waitNotEmpty()
        pasteToCell(FirstCell, "%use notekit")
        runCellAndWaitExecuted(2.minutes)
      }
    }
  }

  @Test
  fun `notekit getCellCount returns correct count`() = withDriver {
    withNotebookEditor {
      step("Add additional cells") {
        pasteKotlinToCurrentCell("val x = 1")
        addKotlinCell("val y = 2")
      }

      step("Get cell count via notekit") {
        addNotekitCodeCell($$"""
          val count = getCellCount()
          appendLine("Cell count: $count")
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify cell count in output") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 5)
        waitFor("Output should contain cell count", timeout = 15.seconds) {
          // We have 4 cells: %use notekit, val x = 1, val y = 2, notekit { getCellCount() }
          // It might happen that `res` cell and the cell added after it are also counted
          (4..6).any { expectedCellCount ->
            lastCellOutputText.contains("Cell count: $expectedCellCount")
          }
        }
      }
    }
  }

  @Test
  fun `notekit getAllCells returns all notebook cells`() = withDriver {
    withNotebookEditor {
      step("Add cells with different content") {
        pasteKotlinToCurrentCell("// First code cell")
        addKotlinCell("// Second code cell")
      }

      step("Get all cells via notekit") {
        addNotekitCodeCell($$"""
          val cells = getAllCells()
          appendLine("Total cells: ${cells.size}")
          cells.forEachIndexed { index, cell ->
              appendLine("Cell $index: ${cell.source}")
          }
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify getAllCells output") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 5)
        waitFor("Output should list all cells", timeout = 15.seconds) {
          lastCellOutputText.let {
            it.contains("Total cells: 4") && it.contains("Cell 2: // Second code cell")
          }
        }
      }
    }
  }

  @Test
  fun `notekit insertCell adds new cell at specified position`() = withDriver {
    withNotebookEditor {
      step("Insert a code cell via notekit") {
        pasteNotekitCodeToCurrentCell($$"""
          val countBefore = getCellCount()
          val newCell = CodeCell(
              id = null,
              source = "println(\"Inserted cell!\")",
              metadata = CodeCellMetadata(),
              executionCount = null,
              outputs = emptyList()
          )
          insertCell(countBefore, newCell)
          appendLine("Inserted cell at position $countBefore")
          appendLine("Cell count after: ${getCellCount()}")
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify cell was inserted") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 3)
        waitFor("Output should confirm insertion", timeout = 15.seconds) {
          lastCellOutputText.let {
            it.contains("Inserted cell at position 2") &&
            (it.contains("Cell count after: 3") || it.contains("Cell count after: 4"))
          }
        }
      }
    }
  }

  @Test
  fun `notekit deleteCell removes cell at specified index`() = withDriver {
    withNotebookEditor {
      step("Add cells") {
        pasteKotlinToCurrentCell("// Cell to be deleted")
        addKotlinCell("// Cell after deletion target")
      }

      step("Delete cell via notekit") {
        addNotekitCodeCell($$"""
          val countBefore = getCellCount()
          appendLine("Cells before deletion: $countBefore")
          deleteCell(1) // Delete the second cell (index 1)
          val countAfter = getCellCount()
          appendLine("Cells after deletion: $countAfter")
        """.trimIndent())
        runAllCells()
        waitCellsAreExecuted(expectedExecutionCount = 3)
      }

      step("Verify cell was deleted") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 4)
        waitFor("Output should show cell count decreased", timeout = 15.seconds) {
          val texts = notebookCellOutputs.lastOrNull()?.getAllTexts()
            .orEmpty().map { it.text }
          // Counts could differ depending on when result cell was executed
          texts.any { "Cells before deletion: " in it } &&
          texts.any { "Cells after deletion: " in it }
        }
      }
    }
  }

  @Test
  @Disabled
  fun `notekit replaceCell modifies existing cell`() = withDriver {
    withNotebookEditor {
      step("Add cell to replace") {
        pasteKotlinToCurrentCell("// Original cell content")
      }

      step("Replace cell via notekit") {
        addNotekitCodeCell($$"""
          val replacementCell = CodeCell(
              id = null,
              source = "// Replaced content",
              metadata = CodeCellMetadata(),
              executionCount = null,
              outputs = emptyList()
          )
          replaceCell(1, replacementCell)
          appendLine("Cell replaced successfully")
          appendLine("Cell count: ${getCellCount()}")
        """.trimIndent())
        runAllCells()
        waitCellsAreExecuted(expectedExecutionCount = 2)
      }

      step("Verify cell was replaced") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 3)
        waitFor("Replacement confirmation in output", timeout = 15.seconds) {
          lastCellOutputText.contains("Cell replaced successfully") &&
          allNotebookTexts.let {
            "// Original cell content" !in it && "// Replaced content" in it
          }
        }
      }
    }
  }

  @Test
  fun `notekit getCell returns specific cell by index`() = withDriver {
    withNotebookEditor {
      step("Add cell with known content") {
        pasteKotlinToCurrentCell("val knownValue = 42 // KNOWN_CELL_MARKER")
      }

      step("Get specific cell via notekit") {
        addNotekitCodeCell($$"""
          val cell = getCell(1)
          appendLine("Cell type: ${cell::class.simpleName}")
          appendLine("Cell source: ${cell.source}")
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify cell content in output") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 4)
        waitFor("Output should show cell info", timeout = 15.seconds) {
          lastCellOutputText.contains("""
            Cell type: CodeCell
            Cell source: val knownValue = 42 // KNOWN_CELL_MARKER
          """.trimIndent())
        }
      }
    }
  }

  @Test
  fun `notekit getCellRange returns cells in range`() = withDriver {
    withNotebookEditor {
      step("Add multiple cells") {
        pasteKotlinToCurrentCell("// Cell A")
        addKotlinCell("// Cell B")
        addKotlinCell("// Cell C")
      }

      step("Get cell range via notekit") {
        addNotekitCodeCell($$"""
          val cells = getCellRange(1, 3)
          appendLine("Range size: ${cells.size}")
          cells.forEachIndexed { i, cell ->
              appendLine("Range cell $i source length: ${cell.source.length}")
          }
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify range output") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 6)
        waitFor("Output should show range info", timeout = 15.seconds) {
          lastCellOutputText.contains("Range size: 2")
        }
      }
    }
  }

  @Test
  fun `notekit getNotebookMetadata returns metadata`() = withDriver {
    withNotebookEditor {
      step("Get notebook metadata via notekit") {
        pasteNotekitCodeToCurrentCell($$"""
          val metadata = getNotebookMetadata()
          appendLine("Metadata: $metadata")
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify metadata output") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 3)
        waitFor("Output should show metadata", timeout = 15.seconds) {
          lastCellOutputText.let {
            "Metadata:" in it && "text/x-kotlin" in it
          }
        }
      }
    }
  }

  @Test
  fun `notekit insertCells adds multiple cells at once`() = withDriver {
    withNotebookEditor {
      step("Insert multiple cells via notekit") {
        pasteNotekitCodeToCurrentCell($$"""
          val countBefore = getCellCount()
          val cellsToInsert = listOf(
              CodeCell(
                  id = null,
                  source = "// Batch cell 1",
                  metadata = CodeCellMetadata(),
                  executionCount = null,
                  outputs = emptyList()
              ),
              CodeCell(
                  id = null,
                  source = "// Batch cell 2",
                  metadata = CodeCellMetadata(),
                  executionCount = null,
                  outputs = emptyList()
              ),
              MarkdownCell(
                  id = null,
                  source = "## Batch markdown",
                  metadata = MarkdownCellMetadata()
              )
          )
          insertCells(countBefore, cellsToInsert)
          appendLine("Inserted ${cellsToInsert.size} cells")
          appendLine("Cell count after: ${getCellCount()}")
        """.trimIndent())
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Verify all cells were inserted") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 3)
        waitFor("Output should confirm batch insertion", timeout = 15.seconds) {
          lastCellOutputText.contains("Inserted 3 cells")
        }
      }
    }
  }

  @Test
  fun `notekit appendCell adds cell to end of notebook`() = withDriver {
    withNotebookEditor {

      step("Append cell via notekit") {
        pasteNotekitCodeToCurrentCell("""
          val newCell = CodeCell(
              id = null,
              source = "// APPEN" + "DED_CELL_AT_END",
              metadata = CodeCellMetadata(),
              executionCount = null,
              outputs = emptyList()
          )
          appendCell(newCell)
          appendLine("Cell appended")
        """.trimIndent())
        runCellAndWaitExecuted(1.minutes, 2)
      }

      step("Verify cell was appended at end") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 3)
        waitFor("Output should confirm append", timeout = 15.seconds) {
          lastCellOutputText.contains("Cell appended")
        }
        // The appended cell should be in the notebook (not necessarily last since result cell comes after)
        waitFor("Appended cell should appear", timeout = 10.seconds) {
          allNotebookTexts.contains("// APPENDED_CELL_AT_END")
        }
      }
    }
  }

  @Test
  fun `notekit executeCell runs specific cell`() = withDriver {
    withNotebookEditor {
      step("Add cell to be executed") {
        pasteKotlinToCurrentCell("val executedResult = \"Executed via notekit!\"")
      }

      step("Execute cell via notekit") {
        addNotekitCodeCell("""
          executeCell(1)
          appendLine("Cell execution triggered")
        """.trimIndent())
        runCellAndWaitExecuted(1.minutes, 3)
      }

      step("Verify cell was executed") {
        waitAndExecuteResultCell(expectedFinalExecutionCount = 4)
        waitFor("Execution confirmation should appear", timeout = 30.seconds) {
          allCellOutputTexts.any { it.contains("Cell execution triggered") }
        }
      }

      step("Verify executed cell result is available") {
        addKotlinCell("executedResult")
        runCellAndWaitExecuted(1.minutes, 5)
        waitFor("Executed result should be available", timeout = 15.seconds) {
          lastCellOutputText.contains("Executed via notekit!")
        }
      }
    }
  }

  private fun NotebookEditorUiComponent.pasteNotekitCodeToCurrentCell(
    @Language("kotlin") block: String
  ) {
    pasteKotlinToCurrentCell(notekitBuildString(block))
  }

  private fun NotebookEditorUiComponent.addNotekitCodeCell(
    @Language("kotlin") block: String
  ) {
    addKotlinCell(notekitBuildString(block))
  }

  private fun notekitBuildString(
    @Language("kotlin") block: String
  ): String {
    return """
      val $RESULT_VARIABLE_NAME = notekit {
          buildString {
              ${block.withIndent(" ".repeat(8))}
          }
      }
    """.trimIndent()
  }

  private fun NotebookEditorUiComponent.waitAndExecuteResultCell(
    expectedFinalExecutionCount: Int,
  ) {
    addKotlinCell(RESULT_VARIABLE_NAME)
    val attemptsCount = 4
    for (attempt in 1..attemptsCount) {
      runCellAndWaitExecuted(1.minutes, expectedFinalExecutionCount)
      if (allCellOutputTexts.none { it.contains("Notekit operation is still in progress") }) break
      if (attempt == attemptsCount) fail("Notekit execution timed out")
      Thread.sleep(1000)
      keyboard { up() }
    }
  }

  private val NotebookEditorUiComponent.lastCellOutputText get() =
    notebookCellOutputs.lastOrNull()?.getAllTexts()?.joinToString("\n") { it.text } ?: ""

  private val NotebookEditorUiComponent.allCellOutputTexts get() =
    notebookCellOutputs
      .flatMap { cellOutput -> cellOutput.getAllTexts() }
      .map { it.text }

  private val NotebookEditorUiComponent.allNotebookTexts get() =
    getAllTexts().joinToString("\n") { it.text }

  companion object {
    private const val RESULT_VARIABLE_NAME = "res"
  }
}