// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.notebookCellLanguageProvider

import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.notebooks.visualization.CodeCellLinesChecker
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.tests.MarkdownRenderModeTestHelper
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.junit.Test

class KotlinNotebookCellLanguageProviderTest : KotlinNotebookBaseTestCase() {

    private val markdownRenderModeHelper = MarkdownRenderModeTestHelper()

    override fun getTestDataPath() = "${baseTestDataPath}/notebooks/cellLanguageProvider"

    override fun setUp() {
        super.setUp()
        markdownRenderModeHelper.setUp()
    }

    override fun tearDown() {
        try {
            markdownRenderModeHelper.tearDown()
        } catch (e: Throwable) {
            throw e
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testKotlinNotebook() {
        myFixture.configureByJupyterFile("kotlinNotebook.ipynb", testDataPath)

        assertCodeCells("kotlin cell, markdown cell, raw cell") {
            markers {
                marker(NotebookCellLines.CellType.CODE, 0, 4, KotlinLanguage.INSTANCE)
                marker(NotebookCellLines.CellType.MARKDOWN, 16, 7, MarkdownLanguage.INSTANCE)
                marker(NotebookCellLines.CellType.RAW, 43, 8, PlainTextLanguage.INSTANCE)
            }
            intervals {
                interval(NotebookCellLines.CellType.CODE, 0..1, KotlinLanguage.INSTANCE)
                interval(NotebookCellLines.CellType.MARKDOWN, 2..3, MarkdownLanguage.INSTANCE)
                interval(NotebookCellLines.CellType.RAW, 4..5, PlainTextLanguage.INSTANCE)
            }
        }
    }

    private fun assertCodeCells(description: String = "", handler: CodeCellLinesChecker.() -> Unit) {
        CodeCellLinesChecker(description) { myFixture.editor as EditorImpl }.invoke(handler)
    }
}
