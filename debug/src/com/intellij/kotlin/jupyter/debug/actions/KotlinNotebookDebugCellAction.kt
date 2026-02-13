// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.actions

import com.intellij.jupyter.core.jupyter.helper.JupyterHelper
import com.intellij.jupyter.core.jupyter.helper.jupyterEditor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.settings.actions.KotlinNotebookEditorActionBase
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.arePresent
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.projectDependencies
import com.intellij.kotlin.jupyter.debug.execution.KotlinNotebookDebugAwareCellExecutorService
import com.intellij.kotlin.jupyter.debug.i18n.KotlinNotebookDebugBundle
import com.intellij.kotlin.jupyter.debug.util.debugActionEnabled
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesSupported
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import kotlinx.coroutines.launch

/**
 * Action to run the current cell with debugging enabled.
 * Unlike normal Run Cell, this creates a non-silent debug session
 */
class KotlinNotebookDebugCellAction : KotlinNotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val dataContext = event.dataContext
        val editor = dataContext.jupyterEditor ?: return
        val project = editor.project ?: return
        val notebookVirtualFile = dataContext.notebookFile ?: return
        if (!notebookVirtualFile.file.isKotlinNotebook) return

        val intervalPointers = JupyterHelper.getSelectedIntervalPointers(editor)
        if (intervalPointers.isEmpty()) return

        val projectScope = KotlinNotebookPluginScope.getForProject(project)
        val executor = KotlinNotebookDebugAwareCellExecutorService.getForFile(project, notebookVirtualFile)

        projectScope.launch {
            executor.executeCellsUnderDebugSession(intervalPointers)
        }
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { event ->
            val presentation = event.presentation
            if (!debugActionEnabled) {
                presentation.isEnabledAndVisible = false
                return@update
            }

            val project = event.project
            val notebook = event.getKotlinNotebook()
            val notebookFile = event.notebookFile
            if (project == null || notebook == null || notebookFile == null) {
                presentation.isEnabledAndVisible = false
                return@update
            }

            val canDebugNow = notebookFile.debugFeaturesSupported(project)
            val hasDependencies = notebookFile.projectDependencies(project).arePresent()
            presentation.isEnabled = canDebugNow && hasDependencies
            presentation.updateTextDescription()
        }
    }

    private fun Presentation.updateTextDescription() {
        if (isEnabledAndVisible) {
            text = KotlinNotebookDebugBundle.message("action.KotlinNotebookDebugCellAction.text")
        } else {
            text = KotlinNotebookDebugBundle.message("action.KotlinNotebookDebugCellAction.disabled.hint")
        }
    }
}

