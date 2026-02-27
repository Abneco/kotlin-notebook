// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.performancePlugin.commands

import com.intellij.jupyter.core.editor.getAllIntervalPointers
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand

/**
 * Command runs all cells in the current notebook (equivalent to NotebookRunAllAction).
 * Example: %NotebookRunAllActions
 */
class NotebookRunAllCellsCommand(text: String, line: Int) : AbstractKotlinJupyterCommand(text, line) {
  companion object {
    const val PREFIX: String = AbstractCommand.CMD_PREFIX + "NotebookRunAllCells"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val editor = getEditor(context) ?: return

    val backedFile = getBackedFile(context) ?: return
    val pointers = getAllIntervalPointers(editor)
      JupyterExecutionManager.getInstanceOrCreate(project, backedFile).runCells(pointers)
  }
}
