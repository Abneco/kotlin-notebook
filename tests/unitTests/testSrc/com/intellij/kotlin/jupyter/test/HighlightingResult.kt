// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.jetbrains.plugins.notebooks.psi.jupyter.lexer.JupyterNotebookCellHeader

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
        result.filter {
            it.description == MISSING_SCRIPT_RECEIVER_CLASS_CODE
        }.shouldBeEmpty()

        result.filter { info ->
            JupyterNotebookCellHeader.CELL_MARKER in info.text
        }.shouldBeEmpty()

        val errors = result.filter { it.severity == HighlightSeverity.ERROR }

        when (strategy) {
            HighlightCheckStrategy.OnlyValidSyntax, HighlightCheckStrategy.ShadowedErrors -> {
                errors.shouldBeEmpty()
                val actualData = result.filter(filter)
                val documentText = runReadAction { testFixture.editor.document.text }
                expectedData.checkResult(notebookFile, actualData, documentText)
            }
            HighlightCheckStrategy.WithErrors -> {
                errors.shouldNotBeEmpty()
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
        return runReadAction {
            ExpectedHighlightingData(testFixture.editor.document, checkWarnings, checkWeakWarnings, checkInfos)
        }
    }

    private companion object {
        const val MISSING_SCRIPT_RECEIVER_CLASS_CODE = "MISSING_SCRIPT_RECEIVER_CLASS"
    }
}