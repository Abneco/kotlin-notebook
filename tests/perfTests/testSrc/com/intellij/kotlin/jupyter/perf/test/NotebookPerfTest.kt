// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.perf.test

import com.intellij.ide.starter.extended.CachedProjectConfiguration
import com.intellij.ide.starter.extended.TestContextCache
import com.intellij.ide.starter.extended.allure.Subsystems
import com.intellij.ide.starter.extended.idea.ultimate.IdeaUltimateCases
import com.intellij.ide.starter.extended.metrics.publishing.evaluateMetric
import com.intellij.ide.starter.extended.metrics.shared.publishCompletionMetricsToTeamCity
import com.intellij.ide.starter.extended.metrics.shared.publishIndexingLookUpsMetricsToTeamCity
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.performance.runTestWithDriver
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.chooseCompletionCommand
import com.intellij.tools.ide.performanceTesting.commands.doComplete
import com.intellij.tools.ide.performanceTesting.commands.startProfile
import com.intellij.tools.ide.performanceTesting.commands.stopProfile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForProjectOpenProcedures
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import com.jetbrains.performancePlugin.commands.CompletionCommand
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Subsystems.Subsystem("Kotlin Notebook Performance")
class NotebookPerfTest {
    private val projectConfig = CachedProjectConfiguration(
        name = "kotlin-notebook-perf",
        testCase = IdeaUltimateCases.IntelliJHelloWorld,
    )

    private val randomName
        get() = "Notebook" + System.currentTimeMillis()

    @Test
    fun notebookCompletionTest() = notebookCompletionTest {
            waitForProjectOpenProcedures()
            createKotlinNotebookFile(randomName)
            waitForCodeAnalysisFinished()
            profileNewCellCompletion("notebook-completion", "not", "notebook")
    }

    @Test
    fun dataFrameCompletionTest() = notebookCompletionTest {
            // data frame preparation
            waitForProjectOpenProcedures()
            createKotlinNotebookFile(randomName)
            addCodeCell("%use dataframe")
            runAllCells()
            waitExecutionFinishes()
            waitForCodeAnalysisFinished()
            // restart kernel to clear the state. Run again and wait for smart mode
            restartKernel()
            runAllCells()
            waitExecutionFinishes()
            waitForSmartMode()
            waitForCodeAnalysisFinished()
            // profiling
            profileNewCellCompletion("dataframe-completion", "DataFrame", "DataFrameBuilder")
    }

    private fun notebookCompletionTest(commands: CommandChain.() -> CommandChain) {
        val context = TestContextCache.getOrCreateTestContext(projectConfig)
        runCompletionTestWithDriver(context, commands = commands(CommandChain()), withScreenRecording = true, timeout = 10.minutes)
    }

    private fun CommandChain.profileNewCellCompletion(
        profileName: String,
        textToType: String,
        completionItem: String,
        repeatCount: Int = 10,
    ) = run {
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

    fun runCompletionTestWithDriver(
        context: IDETestContext,
        commands: CommandChain,
        timeout: Duration = 5.minutes,
        withScreenRecording: Boolean = false,
        additionalMetrics: IDEStartResult.() -> List<PerformanceMetrics.Metric> = { listOf() },
    ): IDEStartResult {
        val result = runTestWithDriver(context, commands, timeout) {
            if (withScreenRecording) withScreenRecording()
        }
        val metrics = publishCompletionMetricsToTeamCity(result, additionalMetrics)
        result.frontendStartResultOrSelf.publishIndexingLookUpsMetricsToTeamCity()
        evaluateMetric(metrics, result, CompletionCommand.SPAN_NAME)
        return result
    }
}
