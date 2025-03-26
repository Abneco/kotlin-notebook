// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.actions.RevealFileAction
import com.intellij.kotlin.jupyter.core.projectWizard.common.RECENT_NOTEBOOK_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import java.nio.file.Path

/**
 * Action to reveal the selected notebook in the file explorer.
 */
class RevealNotebookInFinderAction : AnAction(), DumbAware {
    init {
        templatePresentation.text = RevealFileAction.getActionName()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val notebook = e.getData(RECENT_NOTEBOOK_KEY) ?: return
        RevealFileAction.openFile(Path.of(notebook.path.path))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(RECENT_NOTEBOOK_KEY) != null
    }
}
