// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.K2Only
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.string.shouldContain
import org.junit.Ignore
import org.junit.Test

@K2Only("Testing only actual mode")
@RunModeAwareTest
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/codeinsight")
class NotebookIntentionsTest : KotlinNotebookTestCase() {
    @Test
    fun specifyType() = runFileIntentionsTest()

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

        val ktFile = runReadAction {
            getKtFileUnderCaret() ?: error("No caret specified in the host file")
        }
        invokeIntentionsInInjectedFile(ktFile)

        val dataName = getTestName(true) + ".kt.expected"
        val expectedResult = FileUtil.loadFile(getDataFile(dataName), true)

        val textAfter = runReadAction {
            ktFile.text
        }
        textAfter shouldContain expectedResult
    }
}