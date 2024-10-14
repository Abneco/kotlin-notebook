// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.highlighting

import com.intellij.kotlin.jupyter.test.executeCellsAndShutdownKernel
import com.intellij.kotlin.jupyter.test.getCells
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.core.moveCaret
import org.junit.Test

class NotebookBaseHighlightingTest : AbstractNotebookHighlightingTest() {
    override val canChangeDocumentDuringHighlighting: Boolean = false

    @Test
    fun testSimpleNotebook() {
        doTest(ResultCheckStrategy.OnlyValidSyntax)
    }

    @Test
    fun testCorrectHighlightingWithMarkdown() {
        doTest(ResultCheckStrategy.OnlyValidSyntax)
    }

    //@Test
    fun testWithShadowedUnresolved() {
        doTest(ResultCheckStrategy.ShadowedErrors)
    }

    //@Test
    fun testResolvedAfterExecution() {
        doTest(ResultCheckStrategy.ShadowedErrors) {
            executeCellsAndShutdownKernel(object : ReceivedMessagesTester {
                override val cellsToExecute: List<Int> = listOf(0)
                override val expectedCellsCount: Int = 2

                override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                    assert(cellNum == cellsToExecute.first())
                }

                override fun doAfterCellRun(cellNum: Int) {
                    assert(cellNum == cellsToExecute.first())
                    val nextCell = it.getCells().get(1)
                    //markHostAsCompleteAnalysisTarget(myFixture.editor.document, nextCell)
                    myFixture.editor.moveCaret(nextCell.startOffset)

                    doTest(ResultCheckStrategy.OnlyValidSyntax)
                }
            }, it)
        }
    }
}