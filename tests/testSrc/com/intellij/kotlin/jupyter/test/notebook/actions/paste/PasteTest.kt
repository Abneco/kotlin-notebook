// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.actions.paste

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import org.junit.Test
import java.awt.datatransfer.StringSelection

class PasteTest : KotlinNotebookTransformerBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/actions/paste"

    @Test
    fun testIndentsAfterPaste() = doTest(
        """
            if (x > 0) {
                break
            }
        """.trimIndent()
    )

    private fun doTest(
        pastedText: String,
    ) {
        val expectedCellText = getTestFile(".expected.kts").readText()

        doSimpleTransformerTest(
            expectedCellText,
        ) {
            CopyPasteManager.getInstance().setContents(StringSelection(pastedText))
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        }
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }
}
