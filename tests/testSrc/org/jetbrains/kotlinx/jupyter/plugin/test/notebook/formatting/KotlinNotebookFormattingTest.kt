// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.formatting

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.Test

class KotlinNotebookFormattingTest : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/formatting"

    @Test
    fun testFormatKotlinCell() = doTest("""
        fun f(i: Int): Int {
            return i * i
        }
        
    """.trimIndent())

    private fun doTest(expectedCellText: String) {
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)

        val doc = myFixture.editor.document
        val actualText = runReadAction {
            doc.text
        }
        TestCase.assertEquals(expectedCellText, actualText)
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }
}
