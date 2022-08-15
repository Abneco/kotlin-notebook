// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.inspections

import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.junit.Test

class KotlinNotebookInspectionsTest : KotlinNotebookBaseTestCase() {
    override fun getTestDataPath() = "${baseTestDataPath}/notebooks/inspections"

    @Test
    fun testUnresolvedVarInAnotherCell() = doTest()

    @Test
    fun testStub() {}

    private fun doTest() {
        myFixture.setCaresAboutInjection(true)
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        val hl = myFixture.doHighlighting()
        myFixture.checkHighlighting(true, true, true)
    }
}
