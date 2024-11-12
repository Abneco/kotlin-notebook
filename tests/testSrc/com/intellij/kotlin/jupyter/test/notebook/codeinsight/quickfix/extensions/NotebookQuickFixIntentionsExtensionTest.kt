// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.quickfix.extensions

import com.intellij.kotlin.jupyter.test.notebook.codeinsight.quickfix.NotebookQuickFixBaseTest
import com.intellij.kotlin.jupyter.test.runners.K1Only
import org.junit.Test

@K1Only("KTNB-839: private modifier is added to an extension function")
class NotebookQuickFixIntentionsExtensionTest : NotebookQuickFixBaseTest("extensions") {
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

    //@Test
    fun testExtensionWithTypeParameter() {
        doTest(1)
    }

}