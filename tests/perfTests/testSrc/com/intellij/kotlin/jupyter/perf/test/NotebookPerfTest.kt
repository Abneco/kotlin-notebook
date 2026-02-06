// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.perf.test

import com.intellij.ide.starter.extended.allure.Subsystems
import com.intellij.ide.starter.extended.data.cases.IdeaUltimateCases
import com.intellij.ide.starter.runner.Starter
import com.intellij.performance.runCompletionTestWithDriver
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.chooseCompletionCommand
import com.intellij.tools.ide.performanceTesting.commands.doComplete
import com.intellij.tools.ide.performanceTesting.commands.startProfile
import com.intellij.tools.ide.performanceTesting.commands.stopProfile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForProjectOpenProcedures
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Subsystems.Subsystem("Kotlin Notebook Performance")
class NotebookPerfTest {
    private var context = lazy {
        Starter.newContext(
            "completion-test",
            IdeaUltimateCases.IntelliJHelloWorld,
        )
    }

    private val randomName
        get() = "Notebook" + System.currentTimeMillis()

    @Test
    fun notebookCompletionTest() {
        val commands = CommandChain()
            .waitForProjectOpenProcedures()
            .createKotlinNotebookFile(randomName)
            .waitForCodeAnalysisFinished()
            .profileNewCellCompletion("notebook-completion", "not", "notebook")

        runCompletionTestWithDriver(context.value, commands = commands, timeout = 10.minutes)
    }

    @Test
    fun dataFrameCompletionTest() {
        val commands = CommandChain()
            // data frame preparation
            .waitForProjectOpenProcedures()
            .createKotlinNotebookFile(randomName)
            .addCodeCell("%use dataframe")
            .runAllCells()
            .waitExecutionFinishes()
            .waitForCodeAnalysisFinished()
            // restart kernel to clear the state. Run again and wait for smart mode
            .restartKernel()
            .runAllCells()
            .waitExecutionFinishes()
            .waitForSmartMode()
            .waitForCodeAnalysisFinished()
            // profiling
            .profileNewCellCompletion("dataframe-completion", "DataFrame", "DataFrameBuilder")

        runCompletionTestWithDriver(context.value, commands = commands, timeout = 10.minutes)
    }

    private fun CommandChain.profileNewCellCompletion(profileName: String, textToType: String, completionItem: String, repeatCount: Int = 10) = run {
        startProfile(profileName)
            .run {
                repeat(repeatCount) {
                    this.addCodeCell(textToType)
                        .doComplete()
                        .chooseCompletionCommand(completionItem)
                }
                this.stopProfile()
            }
    }
}


