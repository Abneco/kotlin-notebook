// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.settings.sessionRunMode
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import org.junit.Rule
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

fun assertEquals(expectedOutput: ObjectNode, actualOutput: ObjectNode) {
    org.junit.Assert.assertEquals(expectedOutput.toPrettyString(), actualOutput.toPrettyString())
}


abstract class KotlinNotebookExecutionBaseTestCase : KotlinNotebookBaseTestCase() {
    @JvmField
    @Rule
    var timeout: TestRule = DisableOnDebug(
        CoroutinesTimeout.seconds(180)
    )

    override lateinit var originalVirtualFile: VirtualFile

    override fun runInDispatchThread() = false

    @Suppress("MoveLambdaOutsideParentheses")
    override fun tearDown() {
        listOf(
            { super.tearDown() },
            // Uncomment for testing project leak
            // { TestApplicationManager.testProjectLeak() }
        ).forEachGuaranteed { it() }
    }

    protected fun configureExecutionTest(): PsiFile {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, javaClass)
        myFixture.setCaresAboutInjection(true)

        // If something is executed before highlighting is invoked,
        // it may trigger daemon restarting later asynchronously
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

        val backedFile = invokeAndWaitIfNeeded {
            val backedNotebookVirtualFile = configureByJupyterFile()
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
            backedNotebookVirtualFile
        }
        originalVirtualFile = myFixture.file.virtualFile // `myFixture.file` may return the file which is injected inside one of the cells
        invokeAndWaitIfNeeded {
            backedFile.notebook.sessionRunMode = testContext.kernelRunMode
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        val notebookFile = runReadAction {
            FileContextUtil.getFileContext(myFixture.file)?.containingFile ?: myFixture.file
        }
        setUpProjectSdkIfNeeded()
        return notebookFile
    }
}
