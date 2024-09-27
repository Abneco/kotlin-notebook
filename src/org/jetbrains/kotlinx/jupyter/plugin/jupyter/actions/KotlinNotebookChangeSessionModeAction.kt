// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.jupyter.core.jupyter.helper.editor
import com.intellij.jupyter.core.jupyter.helper.notebook
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.actions.promptSessionShutdownIfNeeded
import org.jetbrains.kotlinx.jupyter.plugin.settings.isAvailable
import org.jetbrains.kotlinx.jupyter.plugin.settings.sessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

sealed class KotlinNotebookChangeSessionModeAction(
  private val mode: KotlinNotebookSessionRunMode,
) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val notebook = event.getKotlinNotebook()
        val isAvailable = mode.isAvailable && notebook != null
        event.presentation.isEnabledAndVisible = isAvailable
        if (isAvailable && notebook.sessionRunMode == mode) {
            event.presentation.icon = AllIcons.Actions.Checked
        }
    }

    private fun AnActionEvent.getKotlinNotebook(): JupyterNotebook? {
        val notebookFile = this.dataContext.notebookFile ?: return null
        return if (notebookFile.isKotlinNotebook) notebookFile.notebook else null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val notebook = event.dataContext.notebook ?: return
        if (notebook.sessionRunMode == mode) return
        promptSessionShutdownIfNeeded(KotlinNotebookChangeSessionModeAction::class, event.editor ?: return) {
            if (notebook.sessionRunMode != mode) {
                WriteAction.run<RuntimeException> {
                    notebook.sessionRunMode = mode
                }
            }
        }
    }
}

class KotlinNotebookEnableSeparateProcessMode : KotlinNotebookChangeSessionModeAction(KotlinNotebookSessionRunMode.SEPARATE_PROCESS)

class KotlinNotebookEnableIdeProcessMode : KotlinNotebookChangeSessionModeAction(KotlinNotebookSessionRunMode.IDE_PROCESS)

class KotlinNotebookEnableAttachedProcessMode : KotlinNotebookChangeSessionModeAction(KotlinNotebookSessionRunMode.ATTACHED_PROCESS)
