// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.notebooks.psi.jupyter.lexer.JupyterNotebookCellHeader
import org.junit.Assert.assertTrue

/**
 * Type-safe wrapper for highlighting results when calling [NotebookTestBuilder.runHighlighting].
 */
class HighlightingResult(
  private val notebookFile: PsiFile,
  private val testFixture: CodeInsightTestFixture,
  val result: List<HighlightInfo>
) {
    /**
     * Check that the highlighting result matches the provided [HighlightCheckStrategy].
     * If not, a test failure is reported
     */
    fun assertHighlightResult(strategy: HighlightCheckStrategy) {
        val filter = createFilterForStrategy(strategy)
        val expectedData = getExpectedHighlightingData(
            checkWarnings = true,
            checkWeakWarnings = false,
            checkInfos = true
        )
        assertTrue(result.none { it.description != null && it.description == scriptingMissingClassError })

        val isHasShadowed = result.any { it.description != null && (it.description.startsWith("Not yet provided symbol") || it.description.startsWith("Improper usage")) }
        assertTrue(result.none { it.text.contains(JupyterNotebookCellHeader.CELL_MARKER) || it.text.contains(
            "${JupyterNotebookCellHeader.CELL_MARKER} ${JupyterNotebookCellHeader.MARKDOWN_CELL_SUFFIX}") })

        when (strategy) {
            HighlightCheckStrategy.OnlyValidSyntax -> {
                //assertTrue(!isHasShadowed)
                assertTrue(result.none { it.severity == HighlightSeverity.ERROR })
                val actualData = result.filter { filter(it) }
                expectedData.checkResult(notebookFile, actualData, testFixture.editor.document.text)
            }
            HighlightCheckStrategy.ShadowedErrors -> {
                //assertTrue(isHasShadowed)
                assertTrue(result.none { it.severity == HighlightSeverity.ERROR })
                val actualData = result.filter { filter(it) }
                expectedData.checkResult(notebookFile, actualData, testFixture.editor.document.text)
            }
            HighlightCheckStrategy.WithErrors -> {
                assertTrue(result.any { it.severity == HighlightSeverity.ERROR })
            }
        }
    }

    private fun createFilterForStrategy(strategy: HighlightCheckStrategy): (HighlightInfo) -> Boolean {
        return when (strategy) {
            HighlightCheckStrategy.ShadowedErrors -> { {
                it.severity.displayName == "INJECTED_FRAGMENT_SYNTAX" || it.severity.displayName == "ERROR"
            } }
            HighlightCheckStrategy.OnlyValidSyntax -> { {
                it.severity.displayName == "INJECTED_FRAGMENT_SYNTAX"
            } }
            HighlightCheckStrategy.WithErrors -> { {
                it.severity.displayName == "ERROR"
            } }
        }
    }

    private fun getExpectedHighlightingData(
        checkWarnings: Boolean,
        checkWeakWarnings: Boolean,
        checkInfos: Boolean
    ): ExpectedHighlightingData {
        return ExpectedHighlightingData(testFixture.editor.document, checkWarnings, checkWeakWarnings, checkInfos)
    }

    private companion object {
        const val scriptingMissingClassError = "MISSING_SCRIPT_RECEIVER_CLASS"
    }
}