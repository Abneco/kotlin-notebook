// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completionWithImport

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester

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