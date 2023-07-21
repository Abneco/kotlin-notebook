// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.hints


import org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.NotebookValuesHintProvider
import org.junit.Test

class NotebookRangeHintsTest : AbstractNotebookTypeHintsBaseTest() {
    override fun getTestDataPath() = "${super.getTestDataPath()}/ranges"
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