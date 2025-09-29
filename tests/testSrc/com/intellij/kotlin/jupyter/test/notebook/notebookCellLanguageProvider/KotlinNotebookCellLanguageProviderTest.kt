// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.notebookCellLanguageProvider

import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.notebooks.visualization.CodeCellLinesChecker
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.TestDataPath
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.tests.MarkdownRenderModeTestHelper
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/cellLanguageProvider")
class KotlinNotebookCellLanguageProviderTest : KotlinNotebookBaseTestCase() {

    private val markdownRenderModeHelper = MarkdownRenderModeTestHelper()

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
        configureByJupyterFile()

        assertCodeCells("kotlin cell, markdown cell, raw cell") {
            markers {
                marker(CellType.CODE, 0, 4, KotlinLanguage.INSTANCE)
                marker(CellType.MARKDOWN, 16, 7, MarkdownLanguage.INSTANCE)
                marker(CellType.RAW, 43, 8, PlainTextLanguage.INSTANCE)
            }
            intervals {
                interval(CellType.CODE, 0..1, KotlinLanguage.INSTANCE)
                interval(CellType.MARKDOWN, 2..3, MarkdownLanguage.INSTANCE)
                interval(CellType.RAW, 4..5, PlainTextLanguage.INSTANCE)
            }
        }
    }

    private fun assertCodeCells(description: String = "", handler: CodeCellLinesChecker.() -> Unit) {
        CodeCellLinesChecker(description) { myFixture.editor as EditorImpl }.invoke(handler)
    }
}
