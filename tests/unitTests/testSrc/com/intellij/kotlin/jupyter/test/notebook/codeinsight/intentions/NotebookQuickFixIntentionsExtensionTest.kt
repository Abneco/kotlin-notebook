// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.K2Only
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.rules.DisableOnDebug
import org.junit.rules.TestRule
import org.junit.rules.Timeout

@K2Only
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/codeinsight/quickfix/extensions")
class NotebookQuickFixIntentionsExtensionTest : KotlinNotebookTestCase() {
    @get:Rule
    val timeoutRule: TestRule = DisableOnDebug(
      Timeout.seconds(120)
    )

    @Test
    fun extensionForClass() = runNotebookTest {
        runIntentionInActiveCell()
        currentCellContent shouldBe getExpectedTestFileContent()
    }

    @Test
    fun extensionForProperty() = runNotebookTest {
        runIntentionInActiveCell()
        currentCellContent shouldBe getExpectedTestFileContent()
    }

    @Test
    fun extensionWithHeteroArguments() = runNotebookTest {
        runIntentionInActiveCell()
        currentCellContent shouldBe getExpectedTestFileContent()
    }

    @Test
    fun extensionWithHomoArguments() = runNotebookTest {
        runIntentionInActiveCell()
        currentCellContent shouldBe getExpectedTestFileContent()
    }

    @Test
    fun extensionWithTypeParameter() = runNotebookTest {
        runIntentionInActiveCell()
        currentCellContent shouldBe getExpectedTestFileContent()
    }
}