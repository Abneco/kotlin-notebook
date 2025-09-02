// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Wrapper representing the result of a completion request, created by calling
 * [CodeInsightTestFixture.completeBasic] an its variants.
 *
 * It represents 3 cases:
 * 1. Success - Completion was successful, but there was only one result that was applied automatically
 * 2. Success - Completion was successful and either 0 or multiple matches was found.
 * 3. Failure - Completion succeeded, but no matches were found.
 *
 * This class automatically converts all [LookupElement]s to a list of [String]s to make it easier to work
 * with them inside tests.
 */
class CompletionResult(originalResult: List<LookupElement?>?, val completedWith: LookupElement? = null) {
    constructor(result: Array<out LookupElement?>?): this(result?.toList())

    // Return the list of lookup as strings or `null` if only a single match was found
    // which has already been applied.
    val result: List<String?>? = originalResult?.map { it?.lookupString }

    fun shouldBeEmpty() {
        assertLookupCount(0)
    }

    fun assertLookupCount(count: Int) {
        when {
            result == null -> count shouldBe 1
            result.isEmpty() -> count shouldBe 0
            else -> count shouldBe result.size
        }
    }

    fun assertLookups(expectedLookupResults: List<String>) {
        if (expectedLookupResults.isEmpty()) error("`expectedLookupResults` should not be empty")
        val lookupResults = result ?: emptyList()
        lookupResults shouldContainAll expectedLookupResults
    }

    fun assertFinishedLookup() {
        completedWith shouldNotBe null
    }

    fun assertFinishedLookupWith(expectedText: String) {
        completedWith?.lookupString shouldBe expectedText
    }
}
