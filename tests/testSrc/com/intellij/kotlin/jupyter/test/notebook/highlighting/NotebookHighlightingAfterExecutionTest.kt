// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Test

class NotebookHighlightingAfterExecutionTest: KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/highlighting"

    @Test
    fun serializationHighlighting() = doTest(object : ReceivedMessagesTester {
        override val expectedCellsCount = 2
        override val cellsToExecute: List<Int> = listOf(0)
        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
        }
    }) { infos ->
        UsefulTestCase.assertNotEmpty(infos)
        val importantInfos = infos.filter { it.severity > HighlightSeverity.INFORMATION }
        UsefulTestCase.assertEmpty(importantInfos)
    }

    private fun doTest(executionTester: ReceivedMessagesTester, hlChecker: (List<HighlightInfo>) -> Unit) {
        doTestAfterExecution(executionTester) {
            runInEdtAndWait {
                val hl = myFixture.doHighlighting()
                hlChecker(hl)
            }
        }
    }
}
