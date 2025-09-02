// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.actions.paste

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/actions/paste")
class PasteTest : KotlinNotebookTestCase() {
    @Test
    fun indentsAfterPaste() = runNotebookTest {
        pasteFromClipboard("""
            if (x > 0) {
                break
            }   
        """.trimIndent())
        currentCellContent shouldBe getExpectedTestFileContent()
    }
}
