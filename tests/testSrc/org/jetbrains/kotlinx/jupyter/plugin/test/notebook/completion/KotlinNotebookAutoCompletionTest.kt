// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.Test

class KotlinNotebookAutoCompletionTest : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/autocompletion"

    internal enum class CompletionMode(val ch: Char) {
        REPLACE('\t'), ADD('\n')
    }

    @Test
    fun testCommandCompletion() = doTest { tester ->
        tester.typeWithPauses("l")
        assertEquals(listOf("help"), lookupStrings)
    }

    @Test
    fun testMagicCompletion() = doTest { tester ->
        tester.typeWithPauses("s")
        assertEquals(listOf("use", "useLatestDescriptors"), lookupStrings)
    }

    @Test
    fun testKotlinCompletionInSameCell() = doTest { tester ->
        tester.typeWithPauses(".")
        assertContainsElements(lookupStrings, "displays", "lastCell", "kernelVersion")
    }

    @Test
    fun testKotlinCompletionInsertionCorrectStd() = doTest { tester ->
        tester.typeWithPauses("li")
        val elements = myFixture?.lookupElements

        assertNoThrowable {
            invokeAndWaitIfNeeded {
                elements?.first { it.lookupString == "listOf" }.let {
                    tester.lookup.finishLookup(CompletionMode.REPLACE.ch, it)
                }
            }
        }

        val t = runReadAction { myFixture.editor.document.text }
        assert(t.contains("listOf<>(x)") )
    }

    @Test
    fun testKotlinCompletionInsertionCorrectReplace() = doTest { tester ->
        tester.typeWithPauses("i")
        val elements = myFixture?.lookupElements

        assertNoThrowable {
            invokeAndWaitIfNeeded {
                elements?.first { it.lookupString == "id" }.let {
                    tester.lookup.finishLookup(CompletionMode.REPLACE.ch, it)
                }
            }
        }

        val t = runReadAction { myFixture.editor.document.text }
        assert(t.contains("id(x)") )
    }

    @Test
    fun testKotlinCompletionInsertionCorrectAdd() = doTest { tester ->
        tester.typeWithPauses("i")
        val elements = myFixture?.lookupElements

        assertNoThrowable {
            invokeAndWaitIfNeeded {
                elements?.first { it.lookupString == "id" }.let {
                    tester.lookup.finishLookup(CompletionMode.ADD.ch, it)
                }
            }
        }

        val t = runReadAction { myFixture.editor.document.text }
        assert(t.contains("id()listOf(x)") )
    }

    @Test
    fun testKotlinCompletionInAnotherCell() = doTest { tester ->
        tester.typeWithPauses(".")
        assertEmpty(lookupStrings)
    }

    private val lookupStrings: List<String> get() = myFixture?.lookupElementStrings.orEmpty()

    private fun doTest(action: (CompletionAutoPopupTester) -> Unit) {
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile

        runInEdtAndWait {
            runReadAction {
                ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
            }
        }

        val completionTester = CompletionAutoPopupTester(myFixture)
        completionTester.runWithAutoPopupEnabled {
            action(completionTester)
        }
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }
}
