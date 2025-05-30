// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.quickfix.extensions

import com.intellij.kotlin.jupyter.test.notebook.codeinsight.quickfix.NotebookQuickFixBaseTest
import com.intellij.kotlin.jupyter.test.runners.K1Only
import com.intellij.testFramework.TestDataPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.TestRule
import org.junit.rules.Timeout

@K1Only("KTNB-839: private modifier is added to an extension function")
@TestDataPath("\$CONTENT_ROOT/testData/notebooks/codeinsight/quickfix/extensions")
class NotebookQuickFixIntentionsExtensionTest : NotebookQuickFixBaseTest() {
    @get:Rule
    val timeoutRule: TestRule = DisableOnDebug(
        Timeout.seconds(120)
    )

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