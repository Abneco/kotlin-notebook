// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.highlighting

import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.kotlinx.jupyter.plugin.test.getCells
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.junit.Test

class NotebookBaseHighlightingTest : AbstractNotebookHighlightingTest() {
    override val canChangeDocumentDuringHighlighting: Boolean = false

    @Test
    fun testSimpleNotebook() {
        doTest(ResultCheckStrategy.OnlyValidSyntax)
    }

    @Test
    fun testWithShadowedUnresolved() {
        doTest(ResultCheckStrategy.ShadowedErrors)
    }

    @Test
    fun testResolvedAfterExecution() {
        doTest(ResultCheckStrategy.ShadowedErrors) {
            executeCells(object : ReceivedMessagesTester {
                override val cellsToExecute: List<Int> = listOf(0)
                override val expectedCellsCount: Int = 2

                override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                    assert(cellNum == cellsToExecute.first())
                }

                override fun doAfterCellRun(
                    cellNum: Int,
                    psiCell: JupyterPsiCell,
                    executionManager: JupyterCellExecutionManager,
                    editor: Editor
                ) {
                    assert(cellNum == cellsToExecute.first())
                    val nextCell = it.getCells().get(1)
                    //markHostAsCompleteAnalysisTarget(myFixture.editor.document, nextCell)
                    myFixture.editor.moveCaret(nextCell.startOffset)

                    doTest(ResultCheckStrategy.OnlyValidSyntax)
                }
            }, it, myFixture.editor)
        }
    }
}