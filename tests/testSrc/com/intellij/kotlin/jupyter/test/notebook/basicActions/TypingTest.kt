// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.basicActions

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.kotlin.jupyter.test.configureBySingleEmptyCellNotebook
import org.junit.Test

class TypingTest: KotlinNotebookTransformerBaseTestCase() {

    @Test
    fun testQuoteHandlingInTheEndOfFile() = doTest(
        "val a = \"",
        "val a = \"\""
    )

    @Test
    fun testBraceHandlingInTheEndOfFile() = doTest(
        "fun f() {\n",
        "fun f() {\n    \n}"
    )

    @Suppress("SameParameterValue")
    private fun doTest(
        textToType: String,
        expectedCellText: String,
    ) {
        doSimpleTransformerTest(
            "#%%\n$expectedCellText",
            testOptions = TestOptions(caresAboutInjection = false, checkTopLevelDocument = true),
            notebookFactory = { myFixture.configureBySingleEmptyCellNotebook() }
        ) {
            myFixture.type(textToType)
        }
    }
}
