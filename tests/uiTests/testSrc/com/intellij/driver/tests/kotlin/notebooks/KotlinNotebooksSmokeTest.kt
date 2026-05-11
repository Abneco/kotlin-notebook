package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.IdeTheme
import com.intellij.driver.sdk.changeTheme
import com.intellij.driver.sdk.dumpThreads
import com.intellij.driver.sdk.getHighlights
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.editor.completionList
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.FirstCell
import com.intellij.driver.sdk.ui.components.notebooks.LastCell
import com.intellij.driver.sdk.ui.components.notebooks.notebookEditor
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.driver.tests.kotlin.notebooks.utils.assertNoKernelRestartNotification
import com.intellij.driver.tests.kotlin.notebooks.utils.checkCreateNewNotebook
import com.intellij.driver.tests.kotlin.notebooks.utils.firstNotebookPlotSpec
import com.intellij.driver.tests.kotlin.notebooks.utils.waitForAndCloseKernelRestartNotification
import com.intellij.driver.tests.kotlin.notebooks.utils.waitForKernelRestartNotification
import com.intellij.jupyter.ui.test.util.codeinsight.checkHasNoErrors
import com.intellij.jupyter.ui.test.util.codeinsight.checkLookAndFeel
import com.intellij.jupyter.ui.test.util.codeinsight.codeAnalysisResults
import com.intellij.jupyter.ui.test.util.codeinsight.isWarning
import com.intellij.jupyter.ui.test.util.completion.checkCompletion
import com.intellij.jupyter.ui.test.util.completion.checkCompletionVariantsWithReport
import com.intellij.jupyter.ui.test.util.kernel.getExecutionTime
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.jupyter.ui.test.util.kernel.testRunCell
import com.intellij.jupyter.ui.test.util.utils.PostExecutionAwaitStrategy
import com.intellij.jupyter.ui.test.util.utils.checkMarkdownCellRendering
import com.intellij.jupyter.ui.test.util.utils.checkRunCellsAndCleanUpOutputs
import com.intellij.jupyter.ui.test.util.utils.runAllCellsRepeatedly
import com.intellij.jupyter.ui.test.util.utils.waitEmpty
import com.intellij.jupyter.ui.test.util.utils.waitFor
import com.intellij.jupyter.ui.test.util.utils.waitNotEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinNotebooksSmokeTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world",
                                                         testRunTimeout = 25.minutes
) {
  @Test
  fun `create new notebook`() = withDriver {
    withNotebookEditor { checkCreateNewNotebook() }
  }

  @Test
  fun `change color theme`() = withDriver {
    ideFrame {
      step("Change color theme to light") {
        changeTheme(IdeTheme.LIGHT)
        notebookEditor { checkLookAndFeel(IdeTheme.LIGHT) }
      }

      step("Change color theme to dark") {
        changeTheme(IdeTheme.DARK)
        notebookEditor { checkLookAndFeel(IdeTheme.DARK) }
      }
    }
  }

  @Test
  fun `run cell and check the output`() = withDriver {
    withNotebookEditor { testRunCell() }
  }

  @Test
  fun `check markdown cell creation`() = withDriver {
    withNotebookEditor { checkMarkdownCellRendering() }
  }

  @Test
  fun `check multiple cell run and execution clean up`() = withDriver {
    withNotebookEditor { checkRunCellsAndCleanUpOutputs() }
  }

  @Test
  fun `check completion`() = withDriver {
    withNotebookEditor {
      step("Run all cells") {
        pasteToCell(FirstCell, "val numbers = mutableListOf(1, 2, 3, 5)")
        runCellAndWaitExecuted(1.minutes)
      }

      step("Check completion for a list method") {
        checkCompletion("numbers.ad", "add")
      }

      step("Check output") {
        val expectedText = "numbers.add()"
        shouldBe("Expected text '$expectedText' not found in the output") {
          text.contains(expectedText)
        }
      }
    }
  }

  @Test
  fun `resolve variable test`() = withDriver {
    withNotebookEditor {
      step("Add variables") {
        addCodeCell("""
          fun <T> id(x: T) = x 
          val numbers = listOf(1, 2, 3, 5)
        """.trimIndent())
        runCellAndWaitExecuted(1.minutes)
      }
      step("Check resolved") {
        typeInCell(LastCell, "println(nu", 200.milliseconds)
        driver.checkCompletionVariantsWithReport("List<Int>", "reference from previous cell")
        keyboard {
          enter() // submit the popup
        }
        driver.codeAnalysisResults(editor).checkHasNoErrors()
      }
    }
  }

  @Test
  fun `check table`() = withDriver {
    withNotebookEditor {
      addKotlinCell("%useLatestDescriptors")
      addKotlinCell("%use dataframe")
      addKotlinCell("")

      step("Create a table") {
        pasteToCell(LastCell, """
          dataFrameOf("name", "age", "origin")("a", "10", "1", "b", "20", "2", "c", "30", null)
          """.trimIndent()
        )
        runAllCellsAndWaitExecuted(1.minutes)
        waitFor("Expect 1 table rendered") {
          notebookTables.size == 1
        }
      }

      step("Check the table") {
        notebookTables.first().run {
        // Check the first row
          tableView.should { getValueAt(0, 0).equals("a") }
        }
      }
    }
  }

  @Test
  fun `restart kernel test`() = withDriver {
    ideFrame {
      withNotebookEditor {
        step("Run a cell") {
          pasteToCell(FirstCell, "print(1)")
          runAllCellsAndWaitExecuted()
        }
        step("Restart kernel") {
          restartKernel()
        }
      }

      step("Check and close restart notification") {
        waitForAndCloseKernelRestartNotification()
      }

      step("Wait a bit") {
        wait(10.seconds)
      }

      step("Make sure restart notification is still gone") {
        assertNoKernelRestartNotification()
      }

      notebookEditor {
        step("Run a cell again") {
          runAllCellsAndWaitExecuted()
        }

        step("Restart kernel again") {
          restartKernel()
        }
      }

      step("Check restart notification again") {
        waitForKernelRestartNotification()
      }
    }
  }

  @Test
  fun `Test use magic`() = withDriver {
    withNotebookEditor {
      step("Check completion for %use") {
        keyboard {
          typeText("%useL")
          checkCompletionVariantsWithReport("useLatestDescriptors", null)
          enter()
        }
      }

      step("Check useLatestDescriptors execution takes less than 20 seconds") {
        runAllCellsAndWaitExecuted()

        val executionTime = notebookCellExecutionInfos.first().getExecutionTime()

        assertTrue(
          executionTime < 20.seconds,
          "Expect execution time < 20s, got $executionTime ms"
        )
      }
    }
  }

  @Test
  fun `Test kandy plot`() = withDriver {
    withNotebookEditor {
      val source = """
                val years = listOf("2017", "2018", "2019", "2020", "2021", "2022", "2023")
                val cost = listOf(56.1, 22.7, 34.7, 82.1, 53.7, 68.5, 39.9)
                plot {
                    area {
                        x(years)
                        y(cost)
                    }
            }""".trimIndent()

      step("Import kandy") {
        keyboard {
          enter()
          typeText("%use kan")
          checkCompletionVariantsWithReport("kandy", null)
          enter()
        }
        runAllCellsAndWaitExecuted(1.minutes)
      }

      step("Create a plot") {
        addKotlinCell(source)
        runAllCellsAndWaitExecuted()
      }

      step("Check plot data we provide to letsPlot library") {
        val actualPlotSpec = waitFor(
          message = "plot should appear",
          timeout = 10.seconds,
          errorMessage = {
            "Can't get plot spec. Currently available plots: " +
            notebookPlots.joinToString("\n") { it.htmlSource }
          },
          getter = { firstNotebookPlotSpec },
          checker = { it != null }
        )
        expectedPlotSpec shouldBe actualPlotSpec
      }

      step("Check the plot's toolbar") {
        notebookPlots.last().run {
          toggleToolbar()
          toolbar.run {
            panButton.component.isShowing() shouldBe true
            rubberBandZoomButton.component.isShowing() shouldBe true
            centerPointZoomButton.component.isShowing() shouldBe true
            resetButton.component.isShowing() shouldBe true
          }
          toggleToolbar()
          toolbar.waitNotFound()
        }
      }
    }
  }

  @Test
  fun `heavy dependencies`() = withDriver {
    withNotebookEditor {
      step("Add heavy dependencies") {
        pasteToCell(
          LastCell,
          """
                %use dataframe, kandy, combinatoricskt, develocity-api-kotlin, adventOfCode
                val col = "name"
              """.trimIndent()
        )
        // a lot of to download
        runCellAndWaitExecuted(5.minutes)
      }

      step("Add another variable") {
        pasteToCell(LastCell, "val data = listOf(1, 2, 3)")
        runCellAndWaitExecuted(expectedFinalExecutionCount = 2)
      }

      step("Check heavy dependencies") {
        withDriver {
          typeInCell(LastCell, "dataFrameO", 200.milliseconds)
          checkCompletionVariantsWithReport(" DataFrame<T>", "dependency from library")

          keyboard {
            enter()
          }
          ui.pasteText("col to data ")
          codeAnalysisResults(editor).checkHasNoErrors()
        }
      }
    }
  }

  @Test
  fun `unused declarations updated`() = withDriver {
    withNotebookEditor {
      step("Add declarations") {
        addKotlinCell("val data = listOf(1, 2, 3)")
        addKotlinCell("""
              val x = 42
              data + x 
            """.trimIndent())
      }

      step("Check contains unused declarations") {
        {
          getHighlights(editor.getDocument())
            .mapNotNull { it.getDescription() }
            .filter { it.contains("is never used") }
        }.waitNotEmpty()
      }

      step("Execute and sync") {
        clickOnCell(LastCell)
        runAllCellsAndWaitExecuted()
      }

      step("Check no unused declarations") {
        { getHighlights(editor.getDocument()).filter { it.isWarning } }.waitEmpty()
      }
    }
  }

  @Test
  fun `update preserves other notebook dependencies`() = withDriver {
    val textToType = "DataFr"

    fun IdeaFrameUI.checkCompletion() = completionList {
      { selectedItems }
        .waitFor("The selected completion item must be 'DataFrame<T>'") {
          it.isNotEmpty() &&
          it.first() == "<T> (org.jetbrains.kotlinx.dataframe) $textToType ame"
        }
    }

    ideFrame {
      withNotebookEditor {
        waitForHighlighting()
        step("Notebook_1: add library dependency") {
          addKotlinCell("%use dataframe")
          runCellAndWaitExecuted(
            2.minutes,
            postExecutionStrategy = PostExecutionAwaitStrategy.AwaitHighlighting
          )
        }
        step("Notebook_1: check completion from library") {
          typeInCell(LastCell, textToType, 200.milliseconds)
          checkCompletion()
          keyboard { enter() }
        }
      }

      // Create a new one
      withNotebookEditor { checkCreateNewNotebook("test NB_2") }

      withNotebookEditor {
        step("Notebook_2: add %use kandy and run to trigger update") {
          addKotlinCell("%use kandy")
          runCellAndWaitExecuted(2.minutes)
        }
        step("Notebook_2: small edit to ensure partial update path is executed") {
          addKotlinCell("val x = 42")
          runCell()
        }
      }

      step("Switch back to Notebook_1") {
        openEditorTabByName("update preserves ")
      }

      withNotebookEditor {
        step("Notebook_1: completion still works after another update") {
          waitForHighlighting()
          typeInCell(FirstCell, textToType, 200.milliseconds)
          checkCompletion()
          keyboard { enter() }
        }
      }
    }
  }

  @Test
  fun `different lib versions are resolved`() = withDriver {
    withNotebookEditor {
      step("Add dependencies") {
        pasteToCell(
          LastCell,
          """
                USE {
                  dependencies { 
                    implementation("org.jetbrains.kotlinx:atomicfu-jvm:0.28.0")
                    implementation("org.jetbrains.kotlinx:atomicfu-jvm:0.23.0")
                  }
                }
                %use dataframe
              """.trimIndent()
        )
        runCellAndWaitExecuted(
          4.minutes,
          postExecutionStrategy = PostExecutionAwaitStrategy.AwaitHighlighting
        )
      }

      step("Check completion") {
        typeInCell(
          LastCell,
          "val data = atomi",
          200.milliseconds
        )

        checkCompletionVariantsWithReport(" Atomic", "'atomic'")

        keyboard {
          enter() // select the only option in the popup
        }

        waitFor {
          text.contains("import kotlinx.atomicfu.")
        }
      }

      step("Check std lib completion") {
        waitForHighlighting()
        addEmptyCodeCell()
        typeInCell(
          LastCell,
          "emptyLi",
          250.milliseconds
        )

        checkCompletionVariantsWithReport(" List", "'emptyList'")
      }
    }
  }

  @Test
  @Disabled("Causes EDT freezes in other tests for some reason")
  fun `fast execution does not break execution labels in Kotlin Notebooks`() = withDriver {
    runAllCellsRepeatedly {
      repeat(10) {
        addKotlinCell("%useLatestDescriptors")
      }
      runAllCellsAndWaitExecuted(2.minutes)
    }
  }

  @BeforeEach
  fun setup(testInfo: TestInfo) = withDriver {
    createNewNotebook(testInfo)
  }

  @AfterEach
  fun gatherDiagnosticInfo(testInfo: TestInfo) = withDriver {
    dumpThreads("diagnosticPostTestThreadDumps", testInfo.displayName)
  }

  //this is the data we provide to letsPlot library
  private val expectedPlotSpec = """
    
    &quot;mapping&quot;:{
    },
    &quot;data&quot;:{
    },
    &quot;kind&quot;:&quot;plot&quot;,
    &quot;scales&quot;:[{
    &quot;aesthetic&quot;:&quot;x&quot;,
    &quot;discrete&quot;:true
    },{
    &quot;aesthetic&quot;:&quot;y&quot;,
    &quot;limits&quot;:[null,null]
    }],
    &quot;layers&quot;:[{
    &quot;mapping&quot;:{
    &quot;x&quot;:&quot;x&quot;,
    &quot;y&quot;:&quot;y&quot;
    },
    &quot;stat&quot;:&quot;identity&quot;,
    &quot;data&quot;:{
    &quot;x&quot;:[&quot;2017&quot;,&quot;2018&quot;,&quot;2019&quot;,&quot;2020&quot;,&quot;2021&quot;,&quot;2022&quot;,&quot;2023&quot;],
    &quot;y&quot;:[56.1,22.7,34.7,82.1,53.7,68.5,39.9]
    },
    &quot;sampling&quot;:&quot;none&quot;,
    &quot;inherit_aes&quot;:false,
    &quot;position&quot;:&quot;identity&quot;,
    &quot;geom&quot;:&quot;area&quot;,
    &quot;data_meta&quot;:{
    &quot;series_annotations&quot;:[{
    &quot;type&quot;:&quot;str&quot;,
    &quot;column&quot;:&quot;x&quot;
    },{
    &quot;type&quot;:&quot;float&quot;,
    &quot;column&quot;:&quot;y&quot;
    }]
    }
    }],
    
  """.trimIndent()
}