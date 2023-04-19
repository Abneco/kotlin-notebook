// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.quickfix.extensions

import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.quickfix.NotebookQuickFixBaseTest
import org.junit.Ignore
import org.junit.Test

class NotebookQuickFixIntentionsExtensionTest : NotebookQuickFixBaseTest() {
    override fun getTestDataPath() = "${super.getTestDataPath()}/extensions"

    @Ignore("Should be investigated in context of KTIJ-25219")
    @Test
    fun testExtensionForClass() {
        doTest(1)
    }

    @Ignore("Should be investigated in context of KTIJ-25219")
    @Test
    fun testExtensionForProperty() {
        doTest(0)
    }

    @Ignore("Should be investigated in context of KTIJ-25219")
    @Test
    fun testExtensionWithHeteroArguments() {
        doTest(1)
    }

    @Ignore("Should be investigated in context of KTIJ-25219")
    @Test
    fun testExtensionWithHomoArguments() {
        doTest(1)
    }

    @Ignore("Until unresolved KtCall is not reported")
    @Test
    fun testExtensionWithTypeParameter() {
        doTest(1)
    }

}