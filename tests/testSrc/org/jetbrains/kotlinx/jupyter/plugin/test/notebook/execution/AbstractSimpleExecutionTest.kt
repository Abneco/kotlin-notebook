// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCellsAndShutdownKernel
import org.jetbrains.kotlinx.jupyter.plugin.test.runners.RunModeAwareTestRunner
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.junit.runner.RunWith

@Suppress("Junit4RunWithInspection")
@RunWith(RunModeAwareTestRunner::class)
abstract class AbstractSimpleExecutionTest : KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/execution"

    protected fun doTest(tester: ReceivedMessagesTester, executionCallback: JupyterExecutionCallback? = null) {
        val notebookFile = configureTestDependencies()
        executeCellsAndShutdownKernel(tester, notebookFile, executionCallback)
    }
}
