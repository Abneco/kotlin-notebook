// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.plugins.notebooks.jackson
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServers
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

interface ReceivedMessages {
    val reply: JupyterMessage?
    val outputs: List<JupyterMessage>
}

data class ReceivedMessagesBuilder(
    override var reply: JupyterMessage? = null,
    override val outputs: MutableList<JupyterMessage> = mutableListOf(),
) : ReceivedMessages

interface ReceivedMessagesTester {
    val expectedCellsCount: Int

    val cellsToExecute: List<Int>
        get() = (0 until expectedCellsCount).toList()

    fun assertCellMessages(cellNum: Int, messages: ReceivedMessages)

    fun doAfterCellRun(cellNum: Int) = Unit
}

val JupyterMessage.messageData get() = messageContent["data"] as ObjectNode

fun textPlainOutput(content: String): ObjectNode = jackson.createObjectNode().apply {
    put("text/plain", content)
}

@RunWith(JUnit4::class)
abstract class KotlinNotebookExecutionBaseTestCase : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
        Disposer.register(testRootDisposable, JupyterServers.getInstance())
    }

    // todo: add to base class
    protected fun setUpScriptingDependencies() {
        val ktFiles = when(val psiFile = myFixture.file) {
            is KtFile -> listOf(psiFile)
            is JupyterFile -> {
                psiFile.getInjectedKtFiles()
            }
            else -> error("Only KtFiles are expected, file passed: ${psiFile}")
        }

        runInEdtAndWait {
            myFixture.project.waitIndexingComplete()
            runReadAction {
                for (file in ktFiles) {
                    ScriptConfigurationManager.updateScriptDependenciesSynchronously(
                        file
                    )
                }
            }
        }

    }


    protected fun configureExecutionTest(
        copyNotebookToProject: Boolean = false,
    ): PsiFile {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, javaClass)
        myFixture.setCaresAboutInjection(true)

        // If something is executed before highlighting is invoked,
        // it may trigger daemon restarting later asynchronously
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

        myFixture.configureByJupyterFile(
            jupyterFileName = "${getTestName(true)}.ipynb",
            testDataPath = testDataPath,
            isCopyToProject = copyNotebookToProject,
        )
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile // `myFixture.file` may return the file which is injected inside one of the cells
        val notebookFile = runReadAction {
            FileContextUtil.getFileContext(myFixture.file)?.containingFile ?: myFixture.file
        }
        return notebookFile
    }

    class OutputsTester(private val cellOutputs: List<List<ObjectNode>>) : ReceivedMessagesTester {
        override val expectedCellsCount: Int
            get() = cellOutputs.size

        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
            KotlinNotebookExecutionTest.log.debug("Checking outputs for cell #$cellNum")
            val expectedOutputs = cellOutputs[cellNum].map { it.toPrettyString() }
            val actualOutputs = messages.outputs.map { it.messageContent["data"].toPrettyString() }
            Assertions.assertIterableEquals(expectedOutputs, actualOutputs)
        }
    }
}
