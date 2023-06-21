// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.HeavyTestHelper
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.plugin.actions.KotlinNotebookCreateAction
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionTest
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesBuilder
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.originFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterBackedVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterBrowserOutputComponentFactory
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointerFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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


fun PsiFile.getCells(): List<JupyterPsiCell> = descendantsOfType<JupyterPsiCell>().toList()

fun PsiFile.isInjectedKtFile(): Boolean = name.endsWith("kts")

fun executeCells(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterExecutionCallback? = null) {
    val project = notebookFile.project
    val document = PsiDocumentManager.getInstance(project).getDocument(notebookFile)!!
    val executionManager = JupyterCellExecutionManager.getInstance(project)
    val notebookCells = notebookFile.getCells()
    val cellsCount = notebookCells.size
    Assertions.assertEquals(tester.expectedCellsCount, cellsCount)

    val cellsToExecute = tester.cellsToExecute
    val cellExecutionNumber = buildMap {
        for ((i, num) in cellsToExecute.withIndex()) {
            put(num, i)
        }
    }
    val receivedMessagesFutures = List(cellsToExecute.size) {
        CompletableFuture<ReceivedMessages>().orTimeout(3, TimeUnit.MINUTES)
    }

    fun endExceptionally(throwable: Throwable) {
        for (future in receivedMessagesFutures) {
            if (!future.isDone) {
                future.completeExceptionally(throwable)
            }
        }
    }

    fun executeCell(cellNumber: Int): Unit = runBlocking {
        KotlinNotebookExecutionTest.log.debug("Executing cell #$cellNumber...")
        val messages = ReceivedMessagesBuilder()
        val cell = notebookCells[cellNumber]
        executionManager.submitTask(readAction {
            val cellPointer = NotebookIntervalPointerFactory.get(project, document)
                .create(NotebookCellLines.get(document).intervals[cellNumber])
            val task =
                JupyterExecutionTask(
                    code = cell.source.text,
                    options = JupyterExecutionTask.Options.cellExecution(cellPointer),
                    onError = { ex: Exception ->
                        endExceptionally(AssertionError("Notebook execution was not successful", ex))
                    },
                    callbacks = listOfNotNull(object : JupyterExecutionCallbackAdapter() {
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
                    }, executionCallback),
                    notebookVirtualFile = cell.getJupyterBackedVirtualFile()!!,
                    project = project
                )
            task
        })
        tester.doAfterCellRun(cellNumber)
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

fun <R> withDisabledJcef(action:() -> R): R {
    return try {
        NotebookOutputComponentFactory.EP_NAME.point.unregisterExtension(JupyterBrowserOutputComponentFactory::class.java)
        action()
    } finally {
        // register extension again?
    }
}

fun Project.createEmptyNotebook(name: String): BackedNotebookVirtualFile {
    val projectBaseDir = HeavyTestHelper.getOrCreateProjectBaseDir(this)
    val directoryPsiFile = runReadAction { PsiManager.getInstance(this).findDirectory(projectBaseDir)!! }

    val psiFile = KotlinNotebookCreateAction.createNotebook(name, directoryPsiFile)
    return BackedNotebookVirtualFile.find(psiFile!!.virtualFile)!!
}

fun BackedNotebookVirtualFile.delete() {
    originFile.let { runWriteAction { it.delete("test") } }
}
