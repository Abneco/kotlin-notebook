// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.LookupFinishMode
import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/autocompletion")
@RunModeAwareTest
class KotlinNotebookAutoCompletionTest : KotlinNotebookTestCase() {

    @Test
    fun commandCompletion() = runNotebookTest {
        typeAndGetLookup("l").assertLookupCount(1)
        currentCellContent.trim() shouldBe ":help"
    }

    @Test
    fun magicCompletion() = runNotebookTest {
        typeAndGetLookup("s").assertLookups(
            listOf("use", "useLatestDescriptors")
        )
    }

    @Test
    fun kotlinCompletionInSameCell() = runNotebookTest {
        typeAndGetLookup(".").assertLookups(
            listOf("displays", "lastCell", "kernelVersion")
        )
    }

    @Test
    fun kotlinCompletionInsertionCorrectStd() = runNotebookTest {
        typeAndFinishLookup("list", LookupFinishMode.TAB) {
            it.lookupString == "listOf"
        }.let {
            val expectedText = when(currentKotlinPluginMode) {
                KotlinPluginMode.K1 -> "listOf(x)"
                KotlinPluginMode.K2 -> "listOf<>()"
            }
            currentCellContent shouldContain expectedText
        }
    }

    @Test
    fun kotlinCompletionInsertionCorrectReplace() = runNotebookTest {
        typeAndFinishLookup("i", LookupFinishMode.TAB) {
            it.lookupString == "id"
        }
        currentCellContent.trim().lines().last() shouldBe "id(x)"
    }

    @Test
    fun kotlinCompletionInsertionCorrectAdd() = runNotebookTest {
        typeAndFinishLookup("i", LookupFinishMode.ENTER) {
            it.lookupString == "id"
        }
        currentCellContent shouldContain "id()listOf(x)"
    }

    @Test
    fun kotlinCompletionOverrideMethod() = runNotebookTest {
        typeAndFinishLookup("de", LookupFinishMode.ENTER) {
            it.allLookupStrings.contains("hashCode")
        }
        currentCellContent.trim() shouldBe """
            class Clazz {
                override fun hashCode(): Int {
                    return super.hashCode()
                }
            }
        """.trimIndent()
    }

    @Test
    fun kotlinCompletionInAnotherCell() = runNotebookTest {
        // Autocomplete should be empty, so just wait for a _reasonable_ amount of time,
        // just in case something was accidentally added to it.
        typeAndGetLookup(".", waitFor = 2.seconds).shouldBeEmpty()
    }
}
