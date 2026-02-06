package com.intellij.kotlin.notebook.performance.testing

import com.intellij.kotlin.notebook.performance.testing.commands.NewKotlinNotebookCommand
import com.intellij.kotlin.notebook.performance.testing.commands.RestartKernelCommand
import com.intellij.kotlin.notebook.performance.testing.commands.WaitExecutionFinishesCommand
import com.jetbrains.performancePlugin.CommandProvider
import com.jetbrains.performancePlugin.CreateCommand

class KotlinNotebookCommandProvider : CommandProvider {
  override fun getCommands(): Map<String, CreateCommand> = mapOf(
      NewKotlinNotebookCommand.PREFIX to CreateCommand(::NewKotlinNotebookCommand),
      WaitExecutionFinishesCommand.PREFIX to CreateCommand(::WaitExecutionFinishesCommand),
      RestartKernelCommand.PREFIX to CreateCommand(::RestartKernelCommand),
    )
}