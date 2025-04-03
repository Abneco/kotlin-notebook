// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.kotlin.jupyter.test.executeCellsAndShutdownKernel
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest

@RunModeAwareTest
abstract class AbstractSimpleExecutionTest : KotlinNotebookExecutionBaseTestCase() {
    protected fun doTest(tester: ReceivedMessagesTester, executionCallback: JupyterExecutionCallback? = null) {
        val notebookFile = configureExecutionTest()
        executeCellsAndShutdownKernel(tester, notebookFile, executionCallback)
    }
}
