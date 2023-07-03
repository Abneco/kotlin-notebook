// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.stats

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.actions.NotebookRunAllAction
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelAction
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelClearOutputsAction
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelRunAllAction
import org.jetbrains.plugins.notebooks.jupyter.editor.getJupyterVirtualFile

class KotlinNotebookActionListener: AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        val project = event.project ?: return
        val backedFile = getJupyterVirtualFile(event) ?: return

        if (backedFile.file.isKotlinNotebook) {
            when(action) {
                is JupyterRestartKernelAction,
                is JupyterRestartKernelRunAllAction,
                is JupyterRestartKernelClearOutputsAction -> {
                    val service = JupyterCompilerService.getForFile(project, backedFile)
                    val cellCountBeforeRestart = service.executedCellsCount
                    val classpathSizeBeforeRestart = service.currentClasspath.size
                    KotlinNotebookFeatureUsagesCollector.registerKernelRestart(project, cellCountBeforeRestart, classpathSizeBeforeRestart)
                }
                is NotebookRunAllAction -> {
                    val cellCountToRun = backedFile.notebook.cells.size
                    KotlinNotebookFeatureUsagesCollector.registerRunAllCells(project, cellCountToRun)
                }
            }
        }
    }
}
