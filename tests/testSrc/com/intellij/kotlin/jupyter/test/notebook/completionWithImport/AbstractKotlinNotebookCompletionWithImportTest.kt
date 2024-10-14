// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completionWithImport

import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester

abstract class AbstractKotlinNotebookCompletionWithImportTest : KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/completionWithImport"
    protected fun doTest(executionTester: ReceivedMessagesTester, completionChecker: (CompletionAutoPopupTester) -> Unit) {
        doTestAfterExecution(executionTester) {
            val completionTester = CompletionAutoPopupTester(myFixture)
            completionTester.runWithAutoPopupEnabled {
                completionChecker(completionTester)
            }
        }
    }
}