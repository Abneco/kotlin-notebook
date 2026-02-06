// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.executor.kernel.JupyterKernelCellTask
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterKernelState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterBrowserOutputComponentFactory
import com.intellij.jupyter.core.kernel.executor.JupyterTaskBaseCallback
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.kotlin.jupyter.core.language.emptyNotebookTemplate
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaPSIFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesBuilder
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.HeavyTestHelper
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.jupyter.builder.NotebookBuilder
import org.jetbrains.jupyter.builder.buildNotebook
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.script.k1.configuration.DefaultScriptingSupport
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.tests.awaitBlocking
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

val baseTestDataPathWithHome = PathManager.getHomePath() + "/plugins/kotlin/jupyter/tests/unitTests/testData"

val CodeInsightTestFixture.kotlinNotebookFile: BackedNotebookVirtualFile?
    get() {
        val virtualFile = file?.virtualFile ?: return null
        return when {
            virtualFile is VirtualFileWindow -> virtualFile.delegate
            else -> virtualFile
        }.toKotlinNotebookBackedFile()
    }

fun PsiFile.getCells(): List<JupyterPsiCell> = descendantsOfType<JupyterPsiCell>().toList()

val defaultTestDuration = 3.minutes

val currentKotlinPluginMode: KotlinPluginMode
    get() {
        val vmValue = System.getProperty("idea.kotlin.plugin.use.k1") ?: return KotlinPluginMode.K2

        return when (vmValue) {
            "true" -> KotlinPluginMode.K1
            else -> KotlinPluginMode.K2
        }
    }

fun <R> runWithJupyterSession(notebookFile: PsiFile, action: () -> R): R {
    val project = notebookFile.project
    val backedFile = notebookFile.virtualFile.toKotlinNotebookBackedFile()!!
    val session = runBlocking {
        JupyterExecutionManager.getInstance(project, backedFile).getOrCreateSession()
    }
    return try {
        action()
    } finally {
        Disposer.dispose(session)
        // make sure to drop previous data
        JupyterCompilerService.getInstance(project).remove(backedFile)
    }
}

fun executeCellsAndShutdownKernel(
    tester: ReceivedMessagesTester,
    notebookFile: PsiFile,
    executionCallback: JupyterTaskBaseCallback? = null
) {
    runWithJupyterSession(notebookFile) {
        executeCells(tester, notebookFile, executionCallback)
    }
}

fun executeCells(tester: ReceivedMessagesTester, notebookFile: PsiFile, executionCallback: JupyterTaskBaseCallback? = null) {
    val project = notebookFile.project
    val document = PsiDocumentManager.getInstance(project).getDocument(notebookFile)!!
    val notebookCells = notebookFile.getCells()
    val cellsCount = notebookCells.size

    cellsCount shouldBe tester.expectedCellsCount

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

    fun executeCell(cellNumber: Int): Unit = runBlocking {
        logger<KotlinNotebookExecutionBaseTestCase>().debug("Executing cell #$cellNumber...")
        val messages = ReceivedMessagesBuilder()
        val cellPointer = NotebookIntervalPointerFactory.get(project, document)
            .create(NotebookCellLines.get(document).intervals[cellNumber])
        val file = notebookFile.virtualFile
        val notebookFile = BackedNotebookVirtualFile.takeIfBacked(file)!!

        val queue = JupyterExecutionManager.getInstance(project, notebookFile)

        val testCallbacks = listOfNotNull(object : JupyterTaskBaseCallback() {
            override fun onStatus(message: JupyterStatusMessage) {
                if (message.executionState == JupyterKernelState.IDLE) {
                    receivedMessages[cellExecutionNumber[cellNumber]!!].complete(messages)
                }
            }

            override fun onExecuteReply(message: JupyterMessage) {
                messages.reply = message
            }

            override fun onUpdateOutput(message: JupyterMessage) {
                messages.outputs.add(message)
            }
        }, executionCallback)
        val kernelCellTask = JupyterKernelCellTask(cellPointer, notebookFile, project, additionalCallbacks = testCallbacks)
        queue.submitTask(kernelCellTask)
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

fun <R> withDisabledJcef(action: () -> R): R {
    return try {
        NotebookOutputComponentFactory.EP_NAME.point.unregisterExtension(JupyterBrowserOutputComponentFactory::class.java)
        action()
    } finally {
        // register extension again?
    }
}

fun Project.createEmptyNotebook(name: String, testRootDisposable: Disposable): BackedNotebookVirtualFile {
    val projectBaseDir = HeavyTestHelper.getOrCreateProjectBaseDir(this)
    val directoryPsiFile = runReadAction { PsiManager.getInstance(this).findDirectory(projectBaseDir)!! }

    val notebookTemplate = directoryPsiFile.project.emptyNotebookTemplate
    val psiFile = CreateNotebookFactory.createFileFromTemplate(
        fileName = name,
        template = notebookTemplate,
        directory = directoryPsiFile,
    )
    val virtualFile = psiFile!!.virtualFile
    return BackedNotebookVirtualFile.getOrLoadForDisposable(virtualFile, disposable = testRootDisposable)!!
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

fun waitForReadyIndexes(fixture: CodeInsightTestFixture) {
    IndexingTestUtil.waitUntilIndexesAreReady(fixture.project)
    DumbService.getInstance(fixture.project).waitForSmartMode()
}

fun PsiFile.getKtFiles(): List<KtFile>? = when (val psiFile = this) {
    is KtFile -> {
        listOf(psiFile)
    }
    is JupyterFile -> {
        runReadAction { psiFile.getInjectedKtFiles() }
    }
    is JKTMetaPSIFile, is JSFile -> {
        // Additional known files that can also be injected
        null
    }
    else -> {
        error("Only KtFiles are expected, file passed: ${psiFile}")
    }
}

/**
 * In production code, we make sure configurations are warmed up during the highlighting.
 * This is especially important for K1 mode
 */
fun ensureScriptConfigurations(fixture: CodeInsightTestFixture) {
    val ktFiles = fixture.file?.getKtFiles() ?: return

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

/** Creates a temporary notebook file using [build] builder. The file is deleted on JVM exit. */
fun buildKotlinNotebookFile(name: String, build: NotebookBuilder.() -> Unit): Path {
    val notebook = buildNotebook("kotlin", "Kotlin", build)
    val notebookFile = FileUtilRt.createTempFile(name, ".ipynb", /* deleteOnExit = */ true)
    notebookFile.outputStream().use { out ->
        @OptIn(ExperimentalSerializationApi::class)
        Json.encodeToStream(notebook, out)
    }
    return notebookFile.toPath()
}

