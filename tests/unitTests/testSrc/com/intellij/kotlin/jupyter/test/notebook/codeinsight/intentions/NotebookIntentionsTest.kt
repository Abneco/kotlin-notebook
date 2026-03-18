// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.string.shouldContain
import org.junit.Ignore
import org.junit.Test

@RunModeAwareTest
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/codeinsight")
class NotebookIntentionsTest : KotlinNotebookTestCase() {
    @Test
    fun specifyTypeForFunction() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        runFileIntentionsTest()
    }

    @Test
    fun specifyTypeForProperty() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        runFileIntentionsTest()
    }

    @Test
    fun convertToBody() = runFileIntentionsTest()

    @Ignore("Until KT-79052 is addressed, exception is thrown, refactoring works")
    @Test
    fun convertFunctionToExtension() = runFileIntentionsTest()

    @Test
    fun convertToInvocation() = runFileIntentionsTest()

    @Test
    fun introduceVariable() = runFileIntentionsTest()

    @Test
    fun addNamesToArguments() = runFileIntentionsTest()

    @Test
    fun introduceFunction() = runFileIntentionsTest()

    @Test
    fun introduceCallablesToClass() = runFileIntentionsTest()

    @Test
    fun introduceClass() = runFileIntentionsTest()

    @Test
    fun implementMembers() = runFileIntentionsTest()

    private fun runFileIntentionsTest() = runNotebookTest {
        assertTestFileHasCaret()
        runIntentionInActiveCell()
        val expectedResult = getExpectedTestFileContent()
        val textAfter = currentCellContent
        textAfter shouldContain expectedResult
    }
}