// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.TestLoggerFactory
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.editor.EditorSessionInitializationService
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.jetbrains.plugins.notebooks.jackson
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServers
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.CompletableFuture

interface ReceivedMessages {
    val reply: JupyterMessage?
    val outputs: List<JupyterMessage>
}

data class ReceivedMessagesBuilder(
    override var reply: JupyterMessage? = null,
    override val outputs: MutableList<JupyterMessage> = mutableListOf(),
): ReceivedMessages

interface ReceivedMessagesTester {
    val expectedCellsCount: Int
    fun assertCellMessages(i: Int, messages: ReceivedMessages)

    fun doAfterCellRun(i: Int, psiCell: JupyterPsiCell, executionManager: JupyterCellExecutionManager, editor: Editor) {

    }
}

private val JupyterMessage.messageData get() = messageContent["data"] as ObjectNode

fun textPlainOutput(content: String): ObjectNode = jackson.createObjectNode().apply {
    put("text/plain", content)
}

class KotlinNotebookExecutionTest : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/execution"

    override fun setUp() {
        super.setUp()
        Disposer.register(testRootDisposable, JupyterServers.getInstance())
    }

    @Test
    fun testExample1() = doTest(OutputsTester(listOf(
        listOf(
            textPlainOutput("5")
        ),
        listOf(),
        listOf(
            textPlainOutput("5")
        ),
        listOf()
    )))

    @Ignore("Ignored because of some JCEF problems with project SDK")
    @Test
    fun testDataframe() = doTest(object: ReceivedMessagesTester {
        override val expectedCellsCount: Int get() = 3

        override fun assertCellMessages(i: Int, messages: ReceivedMessages) {
            if (i == 1) {
                val data = messages.outputs.single().messageData
                val html = data["text/html"].asText()
                assertTrue("DataFrame.renderTable" in html)
            }
        }
    })

    @Test
    fun testInterruption() = doTest(object : ReceivedMessagesTester {
        override val expectedCellsCount: Int
            get() = 2

        override fun assertCellMessages(i: Int, messages: ReceivedMessages) {
            if (i == 0) {
                val output = messages.outputs.single().messageContent
                TestCase.assertEquals("stderr", output["name"].asText())
                TestCase.assertEquals("The execution was interrupted", output["text"].asText())
            }
        }

        override fun doAfterCellRun(i: Int, psiCell: JupyterPsiCell, executionManager: JupyterCellExecutionManager, editor: Editor) {
            if (i == 0) {
                EditorSessionInitializationService.getInstance().onSessionInitialized(editor) {
                    Thread.sleep(2000)
                    runReadAction {
                        executionManager.interrupt(psiCell)
                    }
                }
            }
        }
    })

    private fun doTest(tester: ReceivedMessagesTester) {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, this::class.qualifiedName)
        myFixture.setCaresAboutInjection(true)
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile

        val executionManager = JupyterCellExecutionManager.getInstance(project)

        // `myFixture.file` may return the file which is injected inside one of the cells
        val notebookFile = FileContextUtil.getFileContext(myFixture.file)?.containingFile ?: myFixture.file
        val notebookCells = notebookFile.descendantsOfType<JupyterPsiCell>().toList()
        val cellsCount = notebookCells.size
        Assertions.assertEquals(tester.expectedCellsCount, cellsCount)

        val receivedMessagesFutures = List(cellsCount) { CompletableFuture<ReceivedMessages>() }

        fun endExceptionally(throwable: Throwable) {
            for (future in receivedMessagesFutures) {
                if (!future.isDone) {
                    future.completeExceptionally(throwable)
                }
            }
        }

        fun executeCell(cellNumber: Int) {
            log.debug("Executing cell #$cellNumber...")
            val messages = ReceivedMessagesBuilder()
            val cell = notebookCells[cellNumber]
            runReadAction {
                executionManager.executeCode(
                    null,
                    cell,
                    ignoreOutput = false,
                    cleanOutput = true,
                    onError = { ex: Exception ->
                        endExceptionally(AssertionError("Notebook execution was not successful", ex))
                    },
                    callback = object : JupyterExecutionCallbackAdapter() {
                        override fun onStatus(message: JupyterStatusMessage) {
                            if (message.executionState == JupyterStatusMessage.JupyterExecutionState.IDLE) {
                                receivedMessagesFutures[cellNumber].complete(messages)
                            }
                        }

                        override fun onExecuteReply(message: JupyterMessage) {
                            messages.reply = message
                        }

                        override fun onUpdateOutput(message: JupyterMessage) {
                            messages.outputs.add(message)
                        }
                    },
                    silent = false,
                )
            }
            tester.doAfterCellRun(cellNumber, cell, executionManager, myFixture.editor)
        }

        // Run cells in the order they're defined in the notebook
        for (i in 0 until (cellsCount - 1)) {
            receivedMessagesFutures[i].thenRun { executeCell(i + 1) }
        }

        if (cellsCount > 0) {
            executeCell(0)
        }

        for (i in 0 until cellsCount) {
            tester.assertCellMessages(i, receivedMessagesFutures[i].get())
        }
    }

    class OutputsTester(private val cellOutputs: List<List<ObjectNode>>): ReceivedMessagesTester {
        override val expectedCellsCount: Int
            get() = cellOutputs.size

        override fun assertCellMessages(i: Int, messages: ReceivedMessages) {
            log.debug("Checking outputs for cell #$i")
            val expectedOutputs = cellOutputs[i]
            val actualOutputs = messages.outputs.map { it.messageContent["data"] }
            Assertions.assertIterableEquals(expectedOutputs, actualOutputs)
        }
    }
    
    companion object {
        val log = logger<KotlinNotebookExecutionTest>()
    }
}
