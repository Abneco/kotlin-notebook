// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import io.kotest.matchers.shouldBe
import kotlin.io.path.readText

/**
 * Wrapper representing the result of running a [InlayHintsProvider] on a cell.
 *
 * The result is represented as a string with Inlay Hints being represented by a
 * a special format:
 * ```
 * /*<# inlayHint #>*/
 * ```
 * Example:
 * ```
 * for (index in 'a'/*<# ≤ #>*/ .. /*<# ≤ #>*/'z') {}
 * ```
 * Which matches:
 * ```
 * for (index in 'a' ≤ .. ≤ 'z') {}
 * ```
 *
 * See [InlayDumpUtil] for more details on this.
*/
class InlayHintsResult(
    private val testCase: KotlinNotebookTestCase,
    // The exported result of the cell in which the InlayHintProvider was run.
    val result: String
) {
    /**
     * Similar to [shouldBe], but the expected outcome is represented by a ".kt.expected" file.
     * See [KotlinNotebookTestCase.getExpectedTestFile] for more details.
     *
     */
    fun shouldBeEqualToExpectedFile() {
        val expectedInlayResult = testCase.getExpectedTestFile().readText()
        shouldBe(expectedInlayResult)
    }

    /**
     * Check if the generated inlay hints are equal to the expected outcome or
     * an [junit.framework.AssertionFailedError] is thrown.
     *
     * This check is more simple than the one provided by [InlayHintsProviderTestCase.doTestProvider],
     * but since we cannot extend from two abstract classes, this is not available unless we duplicate
     * the logic or refactor the platform test setup.
     *
     * In particular, this means that `// NO_HINTS` is not supported.
     */
    fun shouldBe(exceptedInlayResult: String) {
        result.trim() shouldBe exceptedInlayResult.trim()
    }
}
