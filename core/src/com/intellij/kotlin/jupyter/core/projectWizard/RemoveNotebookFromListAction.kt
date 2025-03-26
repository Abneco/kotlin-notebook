// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.projectWizard.common.NOTEBOOK_TREE_HOLDER_KEY
import com.intellij.kotlin.jupyter.core.projectWizard.common.RECENT_NOTEBOOK_KEY
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Action to remove the selected notebook from the recent notebooks list.
 */
class RemoveNotebookFromListAction : AnAction(), DumbAware {
    init {
        isEnabledInModalContext = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val notebook = e.getData(RECENT_NOTEBOOK_KEY) ?: return

        val exitCode = Messages.showYesNoDialog(
            KotlinNotebookBundle.message("kotlin.notebook.remove.recent.confirmation.question", notebook.path.name),
            KotlinNotebookBundle.message("kotlin.notebook.remove.recent.confirmation.title"),
            KotlinNotebookBundle.message("kotlin.notebook.remove.recent.confirmation.ok"),
            KotlinNotebookBundle.message("kotlin.notebook.remove.recent.confirmation.cancel"),
            Messages.getQuestionIcon()
        )

        if (exitCode == Messages.OK) {
            val options = KotlinNotebookApplicationOptions.get()
            val recentNotebooks = options.recentNotebooks

            // Find the notebook in the list by its path and remove it
            recentNotebooks.removeIf { it.path == notebook.path.path }

            // Trigger UI update using the tree holder
            val treeHolder = e.getData(NOTEBOOK_TREE_HOLDER_KEY)
            if (treeHolder != null) {
                KotlinNotebookPluginScope.global.launch {
                    treeHolder.updateAsync().join()
                    withContext(Dispatchers.EDT) {
                        treeHolder.revalidateTree()
                    }
                }

            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(RECENT_NOTEBOOK_KEY) != null
    }
}
