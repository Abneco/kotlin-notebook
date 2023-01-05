// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionTest
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesBuilder
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CompletableFuture

val baseTestDataPath = PathManager.getHomePath() + "/plugins/kotlin/jupyter/tests/testData"

@RunWith(JUnit4::class)
abstract class KotlinNotebookBaseTestCase : JupyterBaseTestCase() {
    @JvmField
    @Rule
    val kotlinNotebookCommonRule = JupyterCommonRule(
        withClearPasswordSafe = false,
        withProductionDataManagerRule = false,
        withClearJupyterSettings = true
    )
}


fun executeCells(tester: ReceivedMessagesTester, notebookFile: PsiFile, editor: Editor) {
    val project = notebookFile.project
    val executionManager = JupyterCellExecutionManager.getInstance(project)
    val notebookCells = notebookFile.descendantsOfType<JupyterPsiCell>().toList()
    val cellsCount = notebookCells.size
    Assertions.assertEquals(tester.expectedCellsCount, cellsCount)

    val cellsToExecute = tester.cellsToExecute
    val cellExecutionNumber = buildMap {
        for ((i, num) in cellsToExecute.withIndex()) {
            put(num, i)
        }
    }
    val receivedMessagesFutures = List(cellsToExecute.size) { CompletableFuture<ReceivedMessages>() }

    fun endExceptionally(throwable: Throwable) {
        for (future in receivedMessagesFutures) {
            if (!future.isDone) {
                future.completeExceptionally(throwable)
            }
        }
    }

    fun executeCell(cellNumber: Int) {
        KotlinNotebookExecutionTest.log.debug("Executing cell #$cellNumber...")
        val messages = ReceivedMessagesBuilder()
        val cell = notebookCells[cellNumber]
        runReadAction {
            executionManager.submitTask(
                JupyterExecutionTask(
                    code = null,
                    psiCell = cell,
                    options = JupyterExecutionTask.Options(
                        onExecutionStartedWhenSubmitted = false,
                        ignoreOutput = false,
                        cleanOutput = true,
                        silent = false,
                    ),
                    onError = { ex: Exception ->
                        endExceptionally(AssertionError("Notebook execution was not successful", ex))
                    },
                    callback = object : JupyterExecutionCallbackAdapter() {
                        override fun onStatus(message: JupyterStatusMessage) {
                            if (message.executionState == JupyterStatusMessage.JupyterExecutionState.IDLE) {
                                receivedMessagesFutures[cellExecutionNumber[cellNumber]!!].complete(messages)
                            }
                        }

                        override fun onExecuteReply(message: JupyterMessage) {
                            messages.reply = message
                        }

                        override fun onUpdateOutput(message: JupyterMessage) {
                            messages.outputs.add(message)
                        }
                    },
                    project = project
                )
            )
        }
        tester.doAfterCellRun(cellNumber, cell, executionManager, editor)
    }

    // Run cells in the order they're defined in the notebook
    for (i in 0 until (cellsToExecute.size - 1)) {
        receivedMessagesFutures[i].thenRun { executeCell(cellsToExecute[i + 1]) }
    }

    if (cellsToExecute.isNotEmpty()) {
        executeCell(cellsToExecute[0])
    }

    for (i in cellsToExecute.indices) {
        tester.assertCellMessages(cellsToExecute[i], receivedMessagesFutures[i].get())
    }
}
