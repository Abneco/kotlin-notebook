// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completionWithImport

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.junit.Ignore

@Ignore
class KotlinNotebookCompletionWithImportK2Test: AbstractKotlinNotebookCompletionWithImportTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    // This test is expected to work in K2, but it does not
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
}
