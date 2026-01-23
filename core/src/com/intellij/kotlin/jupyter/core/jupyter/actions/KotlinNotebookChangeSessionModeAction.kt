// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.jupyter.core.jupyter.helper.notebook
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.actions.KotlinNotebookEditorActionBase
import com.intellij.kotlin.jupyter.core.settings.actions.promptSessionShutdownIfNeeded
import com.intellij.kotlin.jupyter.core.settings.isAvailable
import com.intellij.kotlin.jupyter.core.settings.sessionRunMode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.WriteAction

class KotlinNotebookChangeSessionModeAction(
  private val mode: KotlinNotebookSessionRunMode,
) : KotlinNotebookEditorActionBase() {
    override fun createTemplatePresentation(): Presentation {
        return super.createTemplatePresentation().apply {
            text = mode.title
        }
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { event ->
            val presentation = event.presentation
            val notebook = event.getKotlinNotebook()
            val isAvailable = mode.isAvailable && notebook != null
            presentation.isEnabledAndVisible = isAvailable
            if (isAvailable && notebook.sessionRunMode == mode) {
                presentation.icon = AllIcons.Actions.Checked
            }
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val notebook = event.dataContext.notebook ?: return
        if (notebook.sessionRunMode == mode) return
        val project = event.project ?: return
        val notebookFile = event.notebookFile ?: return
        promptSessionShutdownIfNeeded(project, notebookFile) {
            if (notebook.sessionRunMode != mode) {
                WriteAction.run<RuntimeException> {
                    notebook.sessionRunMode = mode
                }
            }
        }
    }
}
