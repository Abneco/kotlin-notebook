// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completionWithImport

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterBrowserOutputComponentFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.junit.Test

class KotlinNotebookCompletionWithImportTest: KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/completionWithImport"

    override fun runInDispatchThread(): Boolean {
        return false
    }

    @Test
    fun testCompletionWithImport() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int
                get() = 3

            override val cellsToExecute: List<Int>
                get() = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                println("#$cellNum: $messages")
            }
        }
    ) { completionTester ->
        completionTester.typeWithPauses("DASH")
        myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
        completionTester.joinCommit()

        val doc = myFixture.editor.document
        val actualText = runReadAction {
            doc.text
        }
        TestCase.assertEquals("""
            plot {
                line {
                    type(LineType.DASHED)
                }
            }
            
        """.trimIndent(), actualText)
    }

    private fun doTest(executionTester: ReceivedMessagesTester, completionChecker: (CompletionAutoPopupTester) -> Unit) {
        withDisabledJcef {
            val notebookFile = configureExecutionTest()
            executeCells(executionTester, notebookFile, myFixture.editor)

            runInEdtAndWait {
                myFixture.project.waitIndexingComplete()
                runReadAction {
                    ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
                }
            }

            val completionTester = CompletionAutoPopupTester(myFixture)
            completionTester.runWithAutoPopupEnabled {
                completionChecker(completionTester)
            }
        }
    }

    private fun <R> withDisabledJcef(action:() -> R): R {
        return try {
            NotebookOutputComponentFactory.EP_NAME.point.unregisterExtension(JupyterBrowserOutputComponentFactory::class.java)
            action()
        } finally {
            // register extension again?
        }
    }
}
