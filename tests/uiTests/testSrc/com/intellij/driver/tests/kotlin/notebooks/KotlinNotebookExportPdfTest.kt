package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitNotNull
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookExportPdfTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world-kotlin") {

  companion object {
    private lateinit var notebookFile: File
    const val MD_CONTENT = "My md cell"
    const val PRINTLN = "println"
    const val HELLO = "Hello"
    const val WORLD = "world"
    const val HELLO_WORLD = "$HELLO $WORLD"
  }

  @BeforeAll
  fun setupNotebook(testInfo: TestInfo) {
    withDriver {
      createNewNotebook(testInfo)

      withNotebookEditor {
        setCurrentCellText("$PRINTLN(\"$HELLO \" + \"$WORLD\")")
        addMarkdownCell(MD_CONTENT)
        runAllCellsAndWaitExecuted(expectedExecutionCount = 1)

        val projectPath = singleProject().getBasePath()
        val notebookName = testInfo.displayName.replace("[^a-zA-Z0-9-_]".toRegex(), "_")
        notebookFile = File(projectPath, "$notebookName.ipynb")
      }
    }
  }

  @BeforeEach
  fun setup() {
    withDriver {
      openFile(notebookFile.name, waitForCodeAnalysis = false)
    }
  }

  @Test
  fun `export entire notebook as PDF`() = withDriver {
    val pdfText = exportNotebookToPdfAndExtractText(PdfExportOption.ENTIRE_NOTEBOOK)

    pdfText shouldContain HELLO_WORLD
    pdfText shouldContain MD_CONTENT
    pdfText shouldContain PRINTLN

  }

  @Test
  fun `export only outputs as PDF`() = withDriver {
    val pdfText = exportNotebookToPdfAndExtractText(PdfExportOption.ONLY_OUTPUTS)

    pdfText shouldContain HELLO_WORLD
    pdfText shouldNotContain PRINTLN
  }

  @Test
  fun `export markdown and outputs as PDF`() = withDriver {
    val pdfText = exportNotebookToPdfAndExtractText(PdfExportOption.MARKDOWN_AND_OUTPUTS)

    pdfText shouldContain HELLO_WORLD
    pdfText shouldContain MD_CONTENT
    pdfText shouldNotContain PRINTLN
  }

  @AfterEach
  fun cleanupPdfFiles() {
    notebookFile.parentFile?.listFiles { file, _ ->
      file.name.endsWith(".pdf") && file.delete()
    }
  }

  enum class PdfExportOption(val menuItemText: String) {
    ENTIRE_NOTEBOOK("Entire Notebook"),
    ONLY_OUTPUTS("Only Outputs"),
    MARKDOWN_AND_OUTPUTS("Markdown and Outputs")
  }

  private fun Driver.exportNotebookToPdfAndExtractText(exportOption: PdfExportOption) = withNotebookEditor {
    exportPdfButton.click()
    driver.ui.ideFrame().list { byClass("MyList") }.clickItem(exportOption.menuItemText)
    val pdfFile = File(notebookFile.parent, "${notebookFile.nameWithoutExtension}.pdf")

    waitNotNull(
      message = "PDF file was not created or is not ready for reading",
      timeout = 60.seconds,
      interval = 500.milliseconds,
      getter = { tryExtractPdfText(pdfFile) },
    )
  }

  private fun tryExtractPdfText(pdfFile: File): String? {
    if (!pdfFile.exists() || pdfFile.length() == 0L) return null

    return try {
      Loader.loadPDF(pdfFile).use { document ->
        PDFTextStripper().getText(document)
      }
    }
    catch (_: IOException) {
      null
    }
  }
}
