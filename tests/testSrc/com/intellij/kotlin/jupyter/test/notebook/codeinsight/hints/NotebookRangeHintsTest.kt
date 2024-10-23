// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.hints


import com.intellij.kotlin.jupyter.core.editor.codeInsight.NotebookValuesHintProvider
import org.junit.Test

class NotebookRangeHintsTest : AbstractNotebookTypeHintsBaseTest("ranges") {
    override val provider = NotebookValuesHintProvider()

    @Test
    fun testSimpleRanges() {
        doTest(provider, 0)
    }

    @Test
    fun testLimitedRanges() {
        doTest(provider, 1, 1)
    }

    @Test
    fun testLimitedRangesCombinedWithMagics() {
        doTest(provider, 1, 1)
    }

}