// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.kotlinx.jupyter.plugin.test.runWithJupyterSession
import org.jetbrains.kotlinx.jupyter.plugin.test.withDisabledJcef
import org.jetbrains.plugins.notebooks.jackson
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServers
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.Rule
import org.junit.jupiter.api.Assertions
import org.junit.rules.DisableOnDebug
import org.junit.rules.TestRule

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

abstract class KotlinNotebookExecutionBaseTestCase : KotlinNotebookBaseTestCase() {
    @JvmField
    @Rule
    var timeout: TestRule = DisableOnDebug(
        CoroutinesTimeout.seconds(180)
    )

    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
        Disposer.register(testRootDisposable, JupyterServers.getInstance())
    }

    @Suppress("MoveLambdaOutsideParentheses")
    override fun tearDown() {
        listOf(
            { super.tearDown() },
            // Uncomment for testing project leak
            // { TestApplicationManager.testProjectLeak() }
        ).forEachGuaranteed { it() }
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
        if (myFixture.file is KtFile) {
            setUpScriptingDependencies()
        }

        return notebookFile
    }

    protected fun doTestAfterExecution(
        executionTester: ReceivedMessagesTester,
        testAction: () -> Unit
    ) {
        withDisabledJcef {
            val notebookFile = configureExecutionTest(copyNotebookToProject = false)

            runWithJupyterSession(notebookFile) {
                executeCells(executionTester, notebookFile)
                setUpScriptingDependencies()
                testAction()
            }
        }
    }

    class OutputsTester(private val cellOutputs: List<List<ObjectNode>>) : ReceivedMessagesTester {
        override val expectedCellsCount: Int
            get() = cellOutputs.size

        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
            LOG.debug("Checking outputs for cell #$cellNum")
            val expectedOutputs = cellOutputs[cellNum].map { it.toPrettyString() }
            val actualOutputs = messages.outputs.map { it.messageContent["data"].toPrettyString() }
            Assertions.assertIterableEquals(expectedOutputs, actualOutputs)
        }
    }

    companion object {
        private val LOG = logger<KotlinNotebookExecutionBaseTestCase>()
    }
}
