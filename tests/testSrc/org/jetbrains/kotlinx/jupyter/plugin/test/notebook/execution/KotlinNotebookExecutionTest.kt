// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.editor.EditorSessionInitializationService
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.junit.Ignore
import org.junit.Test


class KotlinNotebookExecutionTest : KotlinNotebookExecutionBaseTestCase() {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/execution"

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

        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
            if (cellNum == 1) {
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

        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
            if (cellNum == 0) {
                val output = messages.outputs.single().messageContent
                TestCase.assertEquals("stderr", output["name"].asText())
                TestCase.assertEquals("The execution was interrupted", output["text"].asText())
            }
        }

        override fun doAfterCellRun(cellNum: Int, psiCell: JupyterPsiCell, executionManager: JupyterCellExecutionManager, editor: Editor) {
            if (cellNum == 0) {
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
        val notebookFile = configureExecutionTest()
        executeCells(tester, notebookFile, myFixture.editor)
    }
    
    companion object {
        val log = logger<KotlinNotebookExecutionTest>()
    }
}
