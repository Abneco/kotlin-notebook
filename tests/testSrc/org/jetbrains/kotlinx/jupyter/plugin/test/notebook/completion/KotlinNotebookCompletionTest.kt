// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion

import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.junit.Test

class KotlinNotebookCompletionTest : KotlinNotebookBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/completion"

    @Test
    fun testCommandCompletion() = doTest { elements ->
        assertEquals(listOf("classpath", "help", "vars"), elements)
    }

    @Test
    fun testMagicCompletion() = doTest { elements ->
        assertEquals(listOf("trackClasspath", "trackExecution"), elements)
    }

    @Test
    fun testKotlinDotCompletionInSameCell() = doTest { elements ->
        assertContainsElements(elements, "and", "compareTo", "hashCode")
    }

    @Test
    fun testKotlinIdCompletionInSameCell() = doTest { elements ->
        assertContainsElements(elements, "x2", "xyz")
    }

    @Test
    fun testKotlinCompletionInAnotherCell() = doTest { elements ->
        assertEmpty(elements)
    }

    private fun doTest(check: (List<String>) -> Unit) {
        myFixture.configureByJupyterFile("${getTestName(true)}.ipynb", testDataPath)
        val lookupElements = myFixture.completeBasic()
        check(lookupElements.map { it.lookupString })
    }
}
