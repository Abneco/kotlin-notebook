// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.performancePlugin.commands

import com.intellij.jupyter.core.core.impl.llm.NotebookCellLinesLLmActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand

/**
 * Command adds a new code cell below the currently selected cell in the notebook.
 * Example: %addCodeCellBelow
 */
class AddCodeCellBelowCommand(text: String, line: Int) : AbstractKotlinJupyterCommand(text, line) {
    companion object {
        const val PREFIX: String = AbstractCommand.CMD_PREFIX + "addCodeCellBelow"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val project = context.project
        val editor = getEditor(context) ?: return

        @Suppress("TestOnlyProblems")
        WriteCommandAction.runWriteCommandAction(project) {
            NotebookCellLinesLLmActionUtil.insertCodeCellBelow(editor)
        }
    }
}
