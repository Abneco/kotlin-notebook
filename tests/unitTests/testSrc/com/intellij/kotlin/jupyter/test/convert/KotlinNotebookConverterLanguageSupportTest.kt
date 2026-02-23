// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.convert

import com.intellij.jupyter.convert.PageSize
import com.intellij.jupyter.convert.convertToHtml
import com.intellij.jupyter.core.jupyter.helper.JupyterFileUtils
import com.intellij.kotlin.jupyter.export.pdf.SemanticHighlightResult
import com.intellij.kotlin.jupyter.export.pdf.collectKotlinNotebookSemanticHighlights
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.kotlin.jupyter.test.runners.K2Only
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

/**
 * Tests for Kotlin-specific language support in Jupyter notebook converter.
 *
 * Verifies that:
 * 1. Kotlin language support provider is registered
 * 2. Kotlin notebooks are converted to HTML without errors
 * 3. Semantic highlighting is applied correctly
 */
@TestDataPath("/plugins/kotlin/jupyter/tests/unitTests/testData/notebooks/conversion")
class KotlinNotebookConverterLanguageSupportTest : KotlinNotebookTestCase() {
    @Test
    @TestMetadata("helloWorld.ktnb")
    fun `simple notebook should correctly convert to HTML`() = runNotebookTest(setupScriptDependencies = false) {
        val virtualFile = backedNotebookFile.file
        val htmlResult = convertToHtml(project, JupyterFileUtils.readNotebook(virtualFile), PageSize.Letter, notebookFile = virtualFile)
        val html = htmlResult.first

        html.shouldNotBeNull()
        html.shouldNotBeEmpty()
        html shouldContain "println"
        html shouldContain "Hello, Kotlin"
        html shouldContain "<pre"
        html shouldContain "<code"
    }

    @Test
    @TestMetadata("fibonacci.ktnb")
    fun `semantic highlighting should apply different styles to functions and variables`() = runNotebookTest(setupScriptDependencies = false) {
        val virtualFile = backedNotebookFile.file
        val htmlResult = convertToHtml(project, JupyterFileUtils.readNotebook(virtualFile), PageSize.Letter, notebookFile = virtualFile)
        val html = htmlResult.first

        html shouldContain "fibonacci"
        html shouldContain "<span style="
        html shouldContain "fibNumbers"

        val fibonacciMatch = Regex("""<span style="([^"]+)">fibonacci</span>""").find(html)
        val fibNumbersMatch = Regex("""<span style="([^"]+)">fibNumbers</span>""").find(html)

        fibonacciMatch.shouldNotBeNull()
        fibNumbersMatch.shouldNotBeNull()

        val fibonacciStyle = fibonacciMatch.groupValues[1]
        val fibNumbersStyle = fibNumbersMatch.groupValues[1]

        fibonacciStyle shouldNotBe fibNumbersStyle
    }

    @Test
    @TestMetadata("classes.ktnb")
    fun `semantic highlighting should highlight classes and properties`() = runNotebookTest(setupScriptDependencies = false) {
        val virtualFile = backedNotebookFile.file
        val htmlResult = convertToHtml(project, JupyterFileUtils.readNotebook(virtualFile), PageSize.Letter, notebookFile = virtualFile)
        val html = htmlResult.first

        html shouldContain ">Person<"
        html shouldContain ">person<"
        html shouldContain ">name<"
        html shouldContain ">age<"
        html shouldContain "style="
    }

    @Test
    @K2Only("KTNB-883: K1 semantic highlighting returns different results")
    @TestMetadata("directHighlights.ktnb")
    fun `direct semantic highlights collection should correctly identify symbol types`() = runNotebookTest(setupScriptDependencies = true) {
        val code = backedNotebookFile.notebook.getCell(0).source
        val virtualFile = backedNotebookFile.file

        runInEdtAndWait {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        DumbService.getInstance(project).waitForSmartMode()

        val (ktFile, highlightMap) = collectHighlights(
            code,
            virtualFile
        )

        ktFile.shouldNotBeNull()
        runReadActionBlocking {
            ktFile.text.trim() shouldBe code.trim()
        }

        highlightMap.shouldNotBeNull()
        highlightMap.keys.shouldHaveAtLeastSize(1)

        val highlightsByText = runReadActionBlocking {
            highlightMap.values.groupBy { info ->
                ktFile.text.substring(info.startOffset, info.endOffset)
            }
        }

        val fibonacciHighlights = highlightsByText["fibonacci"].shouldNotBeNull()
        fibonacciHighlights.shouldHaveAtLeastSize(4)
        fibonacciHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.FUNCTION_DECLARATION
        fibonacciHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.PACKAGE_FUNCTION_CALL

        val nHighlights = highlightsByText["n"].shouldNotBeNull()
        nHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.PARAMETER

        val nOffsets = runReadActionBlocking {
            highlightMap.filterValues { info ->
                ktFile.text.substring(info.startOffset, info.endOffset) == "n"
            }.keys
        }
        nOffsets.shouldHaveAtLeastSize(2)
        nOffsets.forEach { offset ->
            highlightMap.filterKeys { it == offset }.size shouldBe 1
        }

        val valueHighlights = highlightsByText["value"].shouldNotBeNull()
        valueHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.PARAMETER

        val resultHighlights = highlightsByText["result"].shouldNotBeNull()
        resultHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY

        val intHighlights = highlightsByText["Int"].shouldNotBeNull()
        intHighlights.shouldHaveAtLeastSize(2)

        val lengthHighlights = highlightsByText["length"].shouldNotBeNull()
        lengthHighlights.map { it.type } shouldContain KotlinHighlightInfoTypeSemanticNames.INSTANCE_PROPERTY
    }

    @Test
    @TestMetadata("duplicateCode.ktnb")
    fun `cell matching by index should distinguish duplicate code cells`() = runNotebookTest(setupScriptDependencies = false) {
        val cell1Code = backedNotebookFile.notebook.getCell(0).source
        val virtualFile = backedNotebookFile.file

        val (ktFile1, _) = collectHighlights(cell1Code, virtualFile, cellIndex = 0)

        val (ktFile2, _) = collectHighlights(cell1Code, virtualFile, cellIndex = 1)

        ktFile1 shouldNotBeSameInstanceAs ktFile2
        readAction {
            ktFile1.text.trim() shouldBe cell1Code.trim()
            ktFile2.text.trim() shouldBe cell1Code.trim()
        }
    }

    @Test
    @TestMetadata("helloWorld.ktnb")
    fun `index mismatch should fall back to correct file`() = runNotebookTest(setupScriptDependencies = true) {
        val codeHelloWorld = backedNotebookFile.notebook.getCell(0).source
        val virtualFile = backedNotebookFile.file

        val (ktFile, _) = collectHighlights(codeHelloWorld, virtualFile, cellIndex = 99)

        readAction {
            ktFile.text.trim() shouldBe codeHelloWorld.trim()
        }
    }

    @Test
    @TestMetadata("mixedCells.ktnb")
    fun `markdown drift should be handled with duplicate code cells`() = runNotebookTest(setupScriptDependencies = false) {
        val code = backedNotebookFile.notebook.getCell(1).source
        val virtualFile = backedNotebookFile.file

        val (ktFile1, _) = collectHighlights(code, virtualFile, cellIndex = 1)

        val (ktFile2, _) = collectHighlights(code, virtualFile, cellIndex = 3)

        ktFile1 shouldNotBeSameInstanceAs ktFile2
    }

    private suspend fun collectHighlights(
        code: String,
        virtualFile: VirtualFile,
        cellIndex: Int = -1,
    ): SemanticHighlightResult {
        return readAction {
            collectKotlinNotebookSemanticHighlights(project, code, virtualFile, cellIndex)
        }
    }
}
