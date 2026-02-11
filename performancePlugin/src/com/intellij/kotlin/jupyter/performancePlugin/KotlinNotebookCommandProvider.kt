// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.performancePlugin

import com.intellij.kotlin.jupyter.performancePlugin.commands.AddCodeCellBelowCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.NewKotlinNotebookCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.RestartKernelCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.WaitExecutionFinishesCommand
import com.intellij.kotlin.jupyter.performancePlugin.commands.NotebookRunAllCellsCommand
import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class KotlinNotebookCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
      NewKotlinNotebookCommand.PREFIX to CreateCommand(::NewKotlinNotebookCommand),
      WaitExecutionFinishesCommand.PREFIX to CreateCommand(::WaitExecutionFinishesCommand),
      RestartKernelCommand.PREFIX to CreateCommand(::RestartKernelCommand),
      AddCodeCellBelowCommand.PREFIX to CreateCommand(::AddCodeCellBelowCommand),
      NotebookRunAllCellsCommand.PREFIX to CreateCommand(::NotebookRunAllCellsCommand),
    )
}