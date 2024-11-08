// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterExecutionQueueManager
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterExecutionTask
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterBrowserOutputComponentFactory
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaPSIFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesBuilder
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion
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
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.tests.awaitBlocking
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.junit.jupiter.api.Assertions
import kotlin.time.Duration.Companion.minutes

val baseTestDataPathWithHome = PathManager.getHomePath() + "/plugins/kotlin/jupyter/tests/testData"
const val baseTestDataPath = "/plugins/kotlin/jupyter/tests/testData"

val CodeInsightTestFixture.kotlinNotebookFile: BackedNotebookVirtualFile?
    get() {
        val virtualFile = file.virtualFile ?: return null
        return when {
            virtualFile is VirtualFileWindow -> virtualFile.delegate
            else -> virtualFile
        }.toKotlinNotebookBackedFile()
    }

fun PsiFile.getCells(): List<JupyterPsiCell> = descendantsOfType<JupyterPsiCell>().toList()

fun PsiFile.isInjectedKtFile(): Boolean = name.endsWith("kts")

val defaultTestDuration = 3.minutes

val currentKotlinPluginMode: KotlinPluginMode
    get() {
        val vmValue = System.getProperty("idea.kotlin.plugin.use.k2") ?: return KotlinPluginMode.K1

        return when (vmValue) {
            "true" -> KotlinPluginMode.K2
            else -> KotlinPluginMode.K1
        }
    }

fun <R> runWithJupyterSession(notebookFile: PsiFile, action: () -> R): R {
    val project = notebookFile.project
    val backedFile = notebookFile.virtualFile.toKotlinNotebookBackedFile()!!
    val session = runBlocking {
        JupyterRuntimeService.getInstance(project).getOrCreateSession(backedFile)
    }
    return try {
        action()
    } finally {
        session.deleteSession()
        // make sure to drop previous data
        JupyterCompilerService.getInstance(project).removeSession(backedFile)
    }
}

fun executeCellsAndShutdownKernel(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterExecutionCallback? = null) {
    runWithJupyterSession(notebookFile) {
        executeCells(tester, notebookFile, executionCallback)
    }
}

fun executeCells(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterExecutionCallback? = null) {
    val project = notebookFile.project
    val document = PsiDocumentManager.getInstance(project).getDocument(notebookFile)!!
    val executionManager = JupyterExecutionQueueManager.getInstance(project)
    val notebookCells = notebookFile.getCells()
    val backedNotebookFile = notebookFile.virtualFile.toKotlinNotebookBackedFile()!!
    val cellsCount = notebookCells.size
    Assertions.assertEquals(tester.expectedCellsCount, cellsCount)

    val testTimeout = defaultTestDuration

    val cellsToExecute = tester.cellsToExecute
    val cellExecutionNumber = buildMap {
        for ((i, num) in cellsToExecute.withIndex()) {
            put(num, i)
        }
    }
    val receivedMessages = List(cellsToExecute.size) {
        CompletableDeferred<ReceivedMessages>()
    }

    fun endExceptionally(throwable: Throwable) {
        for (deferred in receivedMessages) {
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(throwable)
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
                                receivedMessages[cellExecutionNumber[cellNumber]!!].complete(messages)
                            }
                        }

                        override fun onExecuteReply(message: JupyterMessage) {
                            messages.reply = message
                        }

                        override fun onUpdateOutput(message: JupyterMessage) {
                            messages.outputs.add(message)
                        }
                    }, executionCallback),
                  notebookVirtualFile = backedNotebookFile,
                  project = project
                )
            task
        })
        tester.doAfterCellRun(cellNumber)
    }

    // Run cells in the order they're defined in the notebook
    for (i in 0 until (cellsToExecute.size - 1)) {
        receivedMessages[i].invokeOnCompletion { executeCell(cellsToExecute[i + 1]) }
    }

    if (cellsToExecute.isNotEmpty()) {
        executeCell(cellsToExecute[0])
    }

    for (i in cellsToExecute.indices) {
        tester.assertCellMessages(cellsToExecute[i], receivedMessages[i].awaitBlocking(testTimeout))
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

fun CodeInsightTestFixture.configureBySimpleNotebook(
    notebookName: String,
    copyToProject: Boolean = false
) = configureByJupyterFile("$notebookName.ipynb", "$baseTestDataPathWithHome/notebooks/simple", isCopyToProject = copyToProject)

fun CodeInsightTestFixture.configureBySingleEmptyCellNotebook(
    copyToProject: Boolean = false
) = configureBySimpleNotebook("singleEmptyCell", copyToProject = copyToProject)

fun CodeInsightTestFixture.configureBySingleEmptyCellNoCaretNotebook(
    copyToProject: Boolean = false
) = configureBySimpleNotebook("singleEmptyCellNoCaret", copyToProject = copyToProject)

fun waitForReadyIndexes(fixture: CodeInsightTestFixture) {
    runInEdtAndWait {
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    }
}

fun PsiFile.getKtFiles(): List<KtFile>? = when(val psiFile = this) {
    is KtFile -> {
        listOf(psiFile)
    }
    is JupyterFile -> {
        runReadAction { psiFile.getInjectedKtFiles() }
    }
    is JKTMetaPSIFile -> {
        null
    }
    else -> {
        error("Only KtFiles are expected, file passed: ${psiFile}")
    }
}

fun setUpScriptingDependencies(fixture: CodeInsightTestFixture) {
    val ktFiles = fixture.file.getKtFiles() ?: return

    runInEdtAndWait {
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
        runReadAction {
            for (file in ktFiles) {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(
                    file
                )
            }
        }
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    }
}

/**
 * In production code, we make sure configurations are warmed up during the highlighting.
 * This is especially important for K1 mode
 */
fun ensureScriptConfigurations(fixture: CodeInsightTestFixture) {
    val ktFiles = fixture.file.getKtFiles() ?: return

    runInEdtAndWait {
        IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
        runReadAction {
            for (file in ktFiles) {
                DefaultScriptingSupport.getInstance(fixture.project)
                    .getOrLoadConfiguration(file.virtualFile, null)
            }
        }

        dispatchAllInvocationEventsInIdeEventQueue()
        waitForAsyncTaskCompletion()
    }
}

enum class LookupFinishMode(val completionChar: Char) {
    ENTER('\n'),
    TAB('\t');
}
