// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.test.waitIndexingComplete
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.runWithJupyterSession
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCells
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.kotlinx.jupyter.plugin.test.withDisabledJcef
import org.junit.Test

class NotebookHighlightingAfterExecutionTest: KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/highlighting"

    override fun runInDispatchThread(): Boolean {
        return false
    }

    @Test
    fun serializationHighlighting() = doTest(object : ReceivedMessagesTester {
        override val expectedCellsCount = 2
        override val cellsToExecute: List<Int> = listOf(0)
        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
        }
    }) { infos ->
        UsefulTestCase.assertNotEmpty(infos)
        val importantInfos = infos.filter { it.severity > HighlightSeverity.INFORMATION }
        UsefulTestCase.assertEmpty(importantInfos)
    }

    private fun doTest(executionTester: ReceivedMessagesTester, hlChecker: (List<HighlightInfo>) -> Unit) {
        withDisabledJcef {
            val notebookFile = configureExecutionTest(copyNotebookToProject = false)

            runWithJupyterSession(notebookFile) {
                executeCells(executionTester, notebookFile)

                runInEdtAndWait {
                    myFixture.project.waitIndexingComplete()
                    runReadAction {
                        ScriptConfigurationManager.updateScriptDependenciesSynchronously(myFixture.file)
                    }
                    val hl = myFixture.doHighlighting()
                    hlChecker(hl)
                }
            }
        }
    }
}
