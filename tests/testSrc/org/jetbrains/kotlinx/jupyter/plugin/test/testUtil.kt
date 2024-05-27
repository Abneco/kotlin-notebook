// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.CreateNotebookFactory
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaPSIFile
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesBuilder
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.originFile
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterBackedVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterBrowserOutputComponentFactory
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointerFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

val baseTestDataPath = PathManager.getHomePath() + "/plugins/kotlin/jupyter/tests/testData"


fun PsiFile.getCells(): List<JupyterPsiCell> = descendantsOfType<JupyterPsiCell>().toList()

fun PsiFile.isInjectedKtFile(): Boolean = name.endsWith("kts")

data class TestDuration(
    val value: Long,
    val unit: TimeUnit,
)

@Suppress("unused")
fun TestDuration.millis() = unit.toMillis(value)

val defaultTestDuration = TestDuration(3, TimeUnit.MINUTES)

fun initJupyterSession(notebookFile: PsiFile): JupyterNotebookSession {
    val project = notebookFile.project
    val backedFile = BackedNotebookVirtualFile.takeIfBacked(notebookFile.virtualFile)!!
    return runBlocking {
        JupyterRuntimeService.getInstance(project).getOrCreateSession(backedFile)
    }
}

fun <R> runWithJupyterSession(session: JupyterNotebookSession?, notebookFile: PsiFile, action: () -> R): R {
    val currentSession = session ?: initJupyterSession(notebookFile)
    return try {
        action()
    } finally {
        currentSession.deleteSession()
    }
}

fun executeCellsAndShutdownKernel(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterExecutionCallback? = null) {
    runWithJupyterSession(null, notebookFile) {
        executeCells(tester, notebookFile, executionCallback)
    }
}

fun executeCells(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterExecutionCallback? = null) {
    val project = notebookFile.project
    val document = PsiDocumentManager.getInstance(project).getDocument(notebookFile)!!
    val executionManager = JupyterCellExecutionManager.getInstance(project)
    val notebookCells = notebookFile.getCells()
    val cellsCount = notebookCells.size
    Assertions.assertEquals(tester.expectedCellsCount, cellsCount)

    val testTimeout = defaultTestDuration

    val cellsToExecute = tester.cellsToExecute
    val cellExecutionNumber = buildMap {
        for ((i, num) in cellsToExecute.withIndex()) {
            put(num, i)
        }
    }
    val receivedMessagesFutures = List(cellsToExecute.size) {
        CompletableFuture<ReceivedMessages>().orTimeout(testTimeout.value, testTimeout.unit)
    }

    fun endExceptionally(throwable: Throwable) {
        for (future in receivedMessagesFutures) {
            if (!future.isDone) {
                future.completeExceptionally(throwable)
            }
        }
    }

    fun executeCell(cellNumber: Int): Unit = runBlocking {
        logger<KotlinNotebookExecutionBaseTestCase>().debug("Executing cell #$cellNumber...")
        val messages = ReceivedMessagesBuilder()
        val cell = notebookCells[cellNumber]
        executionManager.submitTask(readAction {
            val cellPointer = NotebookIntervalPointerFactory.get(project, document)
                .create(NotebookCellLines.get(document).intervals[cellNumber])
            val task =
                JupyterExecutionTask(
                    source = cell.source.text,
                    options = JupyterExecutionTask.Options.cellExecution(cellPointer),
                    onError = { ex: Exception ->
                        endExceptionally(AssertionError("Notebook execution was not successful", ex))
                    },
                    callbacks = listOfNotNull(object : JupyterExecutionCallbackAdapter() {
                        override fun onStatus(message: JupyterStatusMessage) {
                            if (message.executionState == JupyterExecutionState.IDLE) {
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

    val psiFile = CreateNotebookFactory.createFile(name, directoryPsiFile)
    return BackedNotebookVirtualFile.find(psiFile!!.virtualFile)!!
}

fun BackedNotebookVirtualFile.delete() {
    originFile.let { runWriteAction { it.delete("test") } }
}

fun cartesianProduct(vararg lists: List<Any>): List<Array<Any>> {
    if (lists.isEmpty()) return listOf(emptyArray())

    val head = lists.first()
    val tail = lists.drop(1)

    return head.flatMap { item ->
        cartesianProduct(*tail.toTypedArray()).map { arrayOf(item, *it) }
    }
}

fun CodeInsightTestFixture.configureBySimpleNotebook(notebookName: String) =
    configureByJupyterFile("$notebookName.ipynb", "$baseTestDataPath/notebooks/simple")

fun CodeInsightTestFixture.configureBySingleEmptyCellNotebook() = configureBySimpleNotebook("singleEmptyCell")

fun setUpScriptingDependencies(fixture: CodeInsightTestFixture) {
    val ktFiles = when(val psiFile = fixture.file) {
        is KtFile -> {
            listOf(psiFile)
        }
        is JupyterFile -> {
            runReadAction { psiFile.getInjectedKtFiles() }
        }
        is JKTMetaPSIFile -> {
            return
        }
        else -> {
            error("Only KtFiles are expected, file passed: ${psiFile}")
        }
    }

    runInEdtAndWait {
        fixture.project.waitIndexingComplete()
        runReadAction {
            // probably could be changed to something closer to a production API
            for (file in ktFiles) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(
                    file
                )
            }
            JupyterKtScriptingSupport.updateSynchronously(fixture.project)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    }
}

enum class LookupFinishMode(val completionChar: Char) {
    ENTER('\n'),
    TAB('\t');
}
