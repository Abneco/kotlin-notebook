// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completionWithImport

import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import org.junit.Test

class KotlinNotebookCompletionWithImportK1Test : AbstractKotlinNotebookCompletionWithImportTest() {
    @Test(timeout = 300_000)
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

        assertActualText(
            """
            plot {
                line {
                    type(LineType.DASHED)
                }
            }
            
        """.trimIndent()
        )
    }

    @Test(timeout = 300_000)
    fun completionInsertionCorrectWithExternalImport() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2

            override val cellsToExecute: List<Int> = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                println("#$cellNum: $messages")
            }
        }
    ) { tester ->
        tester.typeAndFinishLookup("fail") { it.lookupString == "fail" && it.userDataString.contains("fail  {...}") }
        assertActualText(
            """
            import org.junit.jupiter.api.fail

            val someVar = 123 + x
            fail {  }id(x)
        """.trimIndent()
        )
    }

    @Test(timeout = 300_000)
    fun completionInsertionWithExternalImportInSecondLine() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2

            override val cellsToExecute: List<Int> = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeAndFinishLookup("ai") { it.lookupString == "fail" && it.userDataString.contains("fail  {...}") }
        assertActualText(
            """
            import org.junit.jupiter.api.fail

            fail {  }
            123
        """.trimIndent()
        )
    }

    @Test(timeout = 300_000)
    fun completionOfRunBlocking() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2
            override val cellsToExecute: List<Int> = listOf(0)
            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeAndFinishLookup("n") { it.lookupString == "runBlocking" && it.userDataString.contains("runBlocking  {...}") }
        assertActualText(
            """
            runBlocking {  }
        """.trimIndent()
        )
    }

    @Test(timeout = 300_000)
    fun completionOfRunBlockingWithImport() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2
            override val cellsToExecute: List<Int> = listOf(0)
            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeAndFinishLookup("n") { it.lookupString == "runBlocking" && it.userDataString.contains("runBlocking  {...}") }
        assertActualText("""
            import kotlinx.coroutines.runBlocking
            
            runBlocking {  }
        """.trimIndent()
        )
    }

    @Test(timeout = 300_000)
    fun completionInsideLambda() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 1
            override val cellsToExecute: List<Int> = emptyList()
            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeAndFinishLookup("printl") { it.lookupString == "println" }
        assertActualText("""
            listOf(1, 2, 42).filter { it % 2 == 0 }.map { println() it.plus() }
        """.trimIndent())
    }
}
