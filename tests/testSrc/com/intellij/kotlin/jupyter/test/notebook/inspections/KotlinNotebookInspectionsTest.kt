// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.kotlin.jupyter.test.notebook.inspections

import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/inspections")
class KotlinNotebookInspectionsTest : KotlinNotebookBaseTestCase() {
    @Test
    fun testUnresolvedVarInAnotherCell() = doTest()

    @Test
    fun testStub() {}

    private fun doTest() {
        myFixture.setCaresAboutInjection(true)
        configureByJupyterFile()
        val hl = myFixture.doHighlighting()
        myFixture.checkHighlighting(true, true, true)
    }
}
