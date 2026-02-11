// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.perf.test

import com.intellij.kotlin.jupyter.performancePlugin.commands.AddCodeCellBelowCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.NewKotlinNotebookCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.NotebookRunAllCellsCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.RestartKernelCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.WaitExecutionFinishesCommand
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.delayType


fun <T : CommandChain> T.createKotlinNotebookFile(fileName: String): T = apply {
    addCommand(NewKotlinNotebookCommand.PREFIX, fileName)
}

fun <T : CommandChain> T.addCodeCell(content: String): T = apply {
    addCommand(AddCodeCellBelowCommand.PREFIX)
    delayType(150, content)
}

fun <T : CommandChain> T.runAllCells(): T = apply {
    addCommand(NotebookRunAllCellsCommand.PREFIX)
}

fun <T : CommandChain> T.waitExecutionFinishes(timeout: Long = 60_000): T = apply {
    addCommand(WaitExecutionFinishesCommand.PREFIX, timeout.toString())
}

fun <T : CommandChain> T.restartKernel(): T = apply {
    addCommand(RestartKernelCommand.PREFIX)
}