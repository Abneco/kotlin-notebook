// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.formatting

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/formatting")
class KotlinNotebookFormattingTest : KotlinNotebookTestCase() {
    @Test
    fun formatKotlinCell() = runNotebookTest {
            reformatCode()
            currentCellContent shouldBe """
                fun f(i: Int): Int {
                    return i * i
                }
                
            """.trimIndent()
    }

    @Test
    fun formatWholeFile() = runNotebookTest {
        reformatFile()
        notebookContent shouldBe """
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
    }
}
