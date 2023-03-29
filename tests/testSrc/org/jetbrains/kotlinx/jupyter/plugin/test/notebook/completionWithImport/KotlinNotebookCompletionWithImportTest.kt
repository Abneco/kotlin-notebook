// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completionWithImport

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion.KotlinNotebookAutoCompletionTest
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

    @Test
    fun completionInsertionCorrectWithExternalImport() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2

            override val cellsToExecute: List<Int> = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                println("#$cellNum: $messages")
            }
        }
    ) { tester ->
        tester.typeWithPauses("fail")
        val elements = myFixture?.lookupElements

        assertNoThrowable {
            invokeAndWaitIfNeeded {
                elements?.first { it.lookupString == "fail" && it.userDataString.contains("fail  {...}")}.let {
                    tester.lookup.finishLookup(KotlinNotebookAutoCompletionTest.CompletionMode.ADD.ch, it)
                }
            }
        }
        tester.joinCommit()

        val actualText = runReadAction { myFixture.editor.document.text }

        TestCase.assertEquals("""
            import org.junit.jupiter.api.fail

            val someVar = 123 + x
            fail {  }id (x)
        """.trimIndent(), actualText)
    }

    @Test
    fun completionInsertionWithExternalImportInSecondLine() = doTest(
        object : ReceivedMessagesTester {
            override val expectedCellsCount: Int = 2

            override val cellsToExecute: List<Int> = listOf(0)

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) = Unit
        }
    ) { tester ->
        tester.typeWithPauses("ai")
        val elements = myFixture?.lookupElements

        assertNoThrowable {
            invokeAndWaitIfNeeded {
                elements?.first { it.lookupString == "fail" && it.userDataString.contains("fail  {...}")}.let {
                    tester.lookup.finishLookup(KotlinNotebookAutoCompletionTest.CompletionMode.ADD.ch, it)
                }
            }
        }
        tester.joinCommit()

        val actualText = runReadAction { myFixture.editor.document.text }

        TestCase.assertEquals("""
            import org.junit.jupiter.api.fail

            fail {  }
            123
        """.trimIndent(), actualText)
    }

    private fun doTest(executionTester: ReceivedMessagesTester, completionChecker: (CompletionAutoPopupTester) -> Unit) {
        withDisabledJcef {
            val notebookFile = configureExecutionTest(copyNotebookToProject = true)
            executeCells(executionTester, notebookFile)

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
