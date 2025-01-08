// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.kotlin.jupyter.test.notebook.completion

import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/completion")
class KotlinNotebookCompletionTest : KotlinNotebookBaseTestCase() {
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
        configureByJupyterFile()
        val lookupElements = myFixture.completeBasic()
        check(lookupElements.map { it.lookupString })
    }
}
