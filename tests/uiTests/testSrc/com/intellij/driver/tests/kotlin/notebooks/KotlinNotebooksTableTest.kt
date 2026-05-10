package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.NotebookTableOutputUi
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.driver.tests.kotlin.notebooks.utils.createNewNotebook
import com.intellij.driver.tests.kotlin.notebooks.utils.getProjectPath
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.tables.checkTableCellContextMenuActions
import com.intellij.jupyter.ui.test.util.tables.checkTablePaging
import com.intellij.jupyter.ui.test.util.tables.checkTableSize
import com.intellij.jupyter.ui.test.util.utils.PostExecutionAwaitStrategy
import com.intellij.jupyter.ui.test.util.utils.waitNotEmpty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class KotlinNotebooksTableTest : KotlinNotebooksBaseTest(
  testProjectResourcePath = "kotlin/notebooks/table-test",
  stopAndCloseNotebooksAfterTest = false,
) {

  @Test
  fun `check the table size`() = withDriver {
    withNotebookEditor { checkTableSize() }
  }

  @Test
  fun `check diving into the nested table and returning to the top`() = withDriver {
    withNotebookEditor {
      firstTable.run {
        step("Click on the cell with the table and make sure the subtable is shown") {
          tableView.clickCell(0, 4)
          tableDimension.should { hasText("4 rows × 2 cols") }
          tableView.should { getValueAt(0, 0) == "Charlotte" }
          waitFor(message = "Expect 'friends[1]' in the table breadcrumbs") {
            tableBreadcrumbs.getAllTexts().any { it.text.contains("friends[1]") }
          }
        }
        step("Go top and check the main table is shown") {
          goTopLevel()
          notebookTables.first().waitAnyTexts {
            it.text.contains("3 rows × 5 cols")
          }
        }
      }
    }
  }

  @Test
  fun `verify table sorting`() = withDriver {
    withNotebookEditor {
      firstTable.run {
        header.moveMouse() //make it scroll into the view port to avoid click missing
        repeat(2) { // the fist click enables the default ascending sorting
          header.getAllTexts().first().click()
        }

        tableView.should { getValueAt(0, 1) == "Charlotte" }
      }
    }
  }

  @Test
  fun `check table cell context menu actions`() = withDriver {
    withNotebookEditor { checkTableCellContextMenuActions(1) }
  }

  @Test
  fun `verify table paging`() = withDriver {
    withNotebookEditor { checkTablePaging() }
  }

  @Test
  fun `verify table grouping`() = withDriver {
    withNotebookEditor {
      step("Prepare: add a new grouped table") {
        createNewNotebook("table grouping")
        addKotlinCell("""
          %use dataframe
          val small = DataFrame.readJson("small.json")
        """.trimIndent())
        addKotlinCell("small.group{city..age}.into(\"${GROPED_COLUMN_NAME}\")")
        runAllCellsAndWaitExecuted(timeout = 2.minutes)
        waitFor {
          notebookTables.size == 1
        }
      }

      notebookTables.last().run {
        val groupedColumn = header.getAllTexts().firstOrNull {
          it.text.contains(GROPED_COLUMN_NAME)
        } ?: error("Grouped column is not found")

        step("Hide the city column") {
          groupedColumn.click()
          header.getAllTexts().map { it.text }.run {
            assertFalse(contains("city"))
          }
        }

        step("Show the city column") {
          groupedColumn.click()
          header.getAllTexts().map { it.text }.run {
            assertTrue(contains("city"))
          }
        }

        step("Check sorting in a grouped columns") {
          header.getAllTexts().firstOrNull {
            it.text.contains("city")
          }?.run {
            click()
            notebookTables.last().tableView.should { getValueAt(0, 2) == "Honolulu" }
          } ?: error("City column is not found")
        }
      }
    }
  }

  @Test
  fun `check table header's context menu actions`() = withDriver {
    withNotebookEditor {
      lastTable.run {
        clickHeaderContextCommand("friends", "Hide Column")
        waitFor(message = "City column is hidden") {
          header.getAllTexts().any { it.text == "friends" }.not()
        }
        clickHeaderContextCommand("id", "Show All Columns")
        waitFor(message = "City column is on") {
          notebookTables.last().header.getAllTexts().any { it.text == "friends" }
        }
      }
    }
  }

  @Test
  fun `Export to file check`() = withDriver {
    withNotebookEditor {
      val fileName = "${getProjectPath()}/exportedFileCsv.csv"
      step("Export the table to a file") {
        lastTable.run {
          exportTo(fileName)
        }
      }

      step("Import the table from the file") {
        createNewNotebook("imported table")
        addKotlinCell("%use dataframe")
        addKotlinCell(
          """
           val exportedFile = DataFrame.readCsv("$fileName")
           exportedFile
        """.trimIndent()
        )

        runAllCellsAndWaitExecuted(timeout = 2.minutes, postExecutionStrategy = PostExecutionAwaitStrategy.AwaitHighlighting)

        step("Check table") {
          waitFor(message = "Expect the table imported") {
            notebookTables.isNotEmpty()
          }

          assertEquals(
            "3 rows × 5 cols",
            lastTable.tableDimension.getAllTexts().first().text
          )
        }
      }
    }
  }

  @Test
  fun `test opening a table in a new tab`() = withDriver {
    val tableContent = ideFrame().notebookEditor().lastTable.run {
      tableView.content().also {
        openInNewTabButton.click()
      }
    }
    ideFrame {
      waitFor(message = "Expect new tab is opened") {
        editorTabs().isTabOpened("table test: Out 2")
      }

      //the new tab opens, and it is not the notebook editor, so can't reuse the existing locator
      x("//div[@class='GridMainPanel'][descendant::div[@class='TableResultView']]", NotebookTableOutputUi::class.java).run {
        waitFor("Table is rendered") {
          tableView.rowCount() > 0
        }

        assertEquals(
          tableContent,
          tableView.content()
        )
      }
    }
  }


  @BeforeAll
  fun beforeAll() = withDriver {
    openFile("table test.ipynb", waitForCodeAnalysis = false)
    hideAllToolWindows()
    withNotebookEditor {
      runAllCellsAndWaitExecuted(timeout = 2.minutes)
    }
  }

  @BeforeEach
  fun openNotebook() = withDriver {
    openFile("table test.ipynb", waitForCodeAnalysis = false)
  }

  @AfterEach
  fun closeNotebook() = withDriver {
    invokeAction("CloseAllEditors")
  }

  private val GROPED_COLUMN_NAME = "NGC"

  private val NotebookEditorUiComponent.firstTable get() = waitForNonEmptyTables().first()
  private val NotebookEditorUiComponent.lastTable get() = waitForNonEmptyTables().last()

  private fun NotebookEditorUiComponent.waitForNonEmptyTables() = (::notebookTables).waitNotEmpty()
}
