// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion

import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.jupyter.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile

class KotlinNotebookCompletionTest : JupyterBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/completion"

    fun testCommandCompletion() = doTest { elements ->
        assertEquals(listOf("classpath", "help", "vars"), elements)
    }

    fun testMagicCompletion() = doTest { elements ->
        assertEquals(listOf("trackClasspath", "trackExecution"), elements)
    }

    fun testKotlinDotCompletionInSameCell() = doTest { elements ->
        assertContainsElements(elements, "and", "compareTo", "hashCode")
    }

    fun testKotlinIdCompletionInSameCell() = doTest { elements ->
        assertContainsElements(elements, "x2", "xyz")
    }

    fun testKotlinCompletionInAnotherCell() = doTest { elements ->
        assertEmpty(elements)
    }

    private fun doTest(check: (List<String>) -> Unit) {
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        val lookupElements = myFixture.completeBasic()
        check(lookupElements.map { it.lookupString })
    }
}
