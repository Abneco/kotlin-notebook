// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completionWithImport

import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Test

//@Ignore
class KotlinNotebookCompletionWithImportK2Test: AbstractKotlinNotebookCompletionWithImportTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    // This test is expected to work in K2, but it does not
    // https://youtrack.jetbrains.com/issue/KTNB-829
    // @Test
    fun testCompletionWithImport() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int
                get() = 3

            override val cellsToExecute: List<Int>
                get() = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                println("#$cellNum: $messages")
            }
        }
    ) { tester ->
        tester.typeAndFinishLookup("DASH") {
            it.lookupString.contains("DASHED")
        }
        assertActualText("""
            plot {
                line {
                    type(LineType.DASHED)
                }
            }
            
        """.trimIndent())
    }


    @Test(timeout = 300_000)
    fun completionInsertionWithExternalImportInSecondLine() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2

            override val cellsToExecute: List<Int> = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeAndFinishLookup("ai") { it.lookupString == "fail" && it.userDataString.contains("{ message: (()") }
        assertActualText(
            """
            import org.junit.jupiter.api.fail

            fail {  }
            123
        """.trimIndent()
        )
    }
}
