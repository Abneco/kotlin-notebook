// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.hints

import com.intellij.kotlin.jupyter.k1.codeinsight.hints.KotlinNotebookReferencesTypeHintsProvider
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.K1Only
import com.intellij.testFramework.TestDataPath
import org.junit.Ignore
import org.junit.Test

@K1Only("Not yet supported in K2")
@TestDataPath("\$CONTENT_ROOT/testData/notebooks/codeinsight/hints/references")
class NotebookReferencesHintsTest : KotlinNotebookTestCase() {

    val provider = KotlinNotebookReferencesTypeHintsProvider()

    @Test
    @Ignore("Correct path to type definition differs on TC")
    fun testProperties() = runNotebookTest {
        runInlayProvider(provider, 0) {
           it.propertyType = true
        }.shouldBeEqualToExpectedFile()
    }
}