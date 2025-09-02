// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.basicActions

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/")
class TypingTest : KotlinNotebookTestCase() {

    @Test
    @TestMetadata("simple/singleEmptyCell.ipynb")
    fun quoteHandlingInTheEndOfFile() = runNotebookTest {
        type("val a = \"")
        currentCellContent shouldBe """
            val a = ""
        """.trimIndent()
    }

    @Test
    @TestMetadata("simple/singleEmptyCell.ipynb")
    fun testBraceHandlingInTheEndOfFile() = runNotebookTest {
        type("fun f() {\n")
        currentCellContent shouldBe "fun f() {\n    \n}"
    }
}
