// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.editor.actions.command.mode.setMode
import org.jetbrains.plugins.notebooks.jupyter.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile

class KotlinNotebookAutoCompletionTest : JupyterBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/autocompletion"

    fun testCommandCompletion() = doTest { tester ->
        tester.typeWithPauses("l")
        assertEquals(listOf("help"), lookupStrings)
    }

    fun testMagicCompletion() = doTest { tester ->
        tester.typeWithPauses("s")
        assertEquals(listOf("use", "useLatestDescriptors"), lookupStrings)
    }

    fun testKotlinCompletionInSameCell() = doTest { tester ->
        tester.typeWithPauses(".")
        assertContainsElements(lookupStrings, "and", "compareTo", "hashCode")
    }

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
        val completionTester = CompletionAutoPopupTester(myFixture)
        completionTester.runWithAutoPopupEnabled {
            action(completionTester)
        }
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }
}
