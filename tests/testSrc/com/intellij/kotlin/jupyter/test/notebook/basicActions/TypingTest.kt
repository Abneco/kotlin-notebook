// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.basicActions

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/")
class TypingTest : KotlinNotebookTransformerBaseTestCase() {

    @Test
    @TestMetadata("simple/singleEmptyCell.ipynb")
    fun testQuoteHandlingInTheEndOfFile() = doTest(
        "val a = \"",
        "val a = \"\""
    )

    @Test
    @TestMetadata("simple/singleEmptyCell.ipynb")
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
        ) {
            invokeAndWaitIfNeeded {
                myFixture.type(textToType)
            }
        }
    }
}
