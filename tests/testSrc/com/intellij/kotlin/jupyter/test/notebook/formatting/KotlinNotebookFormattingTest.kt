// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.formatting

import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrSelf
import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/formatting")
class KotlinNotebookFormattingTest : KotlinNotebookTransformerBaseTestCase() {
    @Test
    fun testFormatKotlinCell() = doTest(
        false,
        """
            fun f(i: Int): Int {
                return i * i
            }
            
        """.trimIndent()
    )

    @Test
    fun testFormatWholeFile() = doTest(
        true,
        """
            #%%
            fun f(i: Int): Int {
                return i * i
            }
            #%%
            %use dataframe
            fun f2(i: Int): Int {
                return i * i
            }
        """.trimIndent()
    )

    private fun doTest(
        reformatWholeFile: Boolean,
        expectedText: String,
    ) {
        doSimpleTransformerTest(
            expectedText,
            TestOptions(
                checkTopLevelDocument = reformatWholeFile,
            ),
        ) {
            if (reformatWholeFile) {
                WriteCommandAction.runWriteCommandAction(project) {
                    CodeStyleManager.getInstance(project).reformat(myFixture.file.getTopLevelFileOrSelf())
                }
            } else {
                myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
            }
        }
    }
}
