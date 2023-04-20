// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.quickfix.extensions

import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.quickfix.NotebookQuickFixBaseTest
import org.junit.Test

class NotebookQuickFixIntentionsExtensionTest : NotebookQuickFixBaseTest() {
    override fun getTestDataPath() = "${super.getTestDataPath()}/extensions"

    @Test
    fun testExtensionForClass() {
        doTest(1)
    }

    @Test
    fun testExtensionForProperty() {
        doTest(0)
    }

    @Test
    fun testExtensionWithHeteroArguments() {
        doTest(1)
    }

    @Test
    fun testExtensionWithHomoArguments() {
        doTest(1)
    }

    @Test
    fun testExtensionWithTypeParameter() {
        doTest(1)
    }

}