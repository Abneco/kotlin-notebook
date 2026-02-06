// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.kotlin.jupyter.test.notebook.completion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/completion")
class KotlinNotebookCompletionTest : KotlinNotebookTestCase() /*KotlinNotebookBaseTestCase()*/ {

    @Test
    fun commandCompletion() = runNotebookTest {
        completeAtCaret().assertLookups(listOf("classpath", "help", "vars"))
    }

    @Test
    fun magicCompletion() = runNotebookTest {
        completeAtCaret().assertLookups(listOf("trackClasspath", "trackExecution"))
    }

    @Test
    fun kotlinDotCompletionInSameCell() = runNotebookTest {
        completeAtCaret().assertLookups(listOf("and", "compareTo", "hashCode"))
    }

    @Test
    fun kotlinIdCompletionInSameCell() = runNotebookTest {
        completeAtCaret().assertLookups(listOf("x2", "xyz"))
    }

    @Test
    fun kotlinCompletionInAnotherCell() = runNotebookTest {
        completeAtCaret().assertLookupCount(0)
    }
}
