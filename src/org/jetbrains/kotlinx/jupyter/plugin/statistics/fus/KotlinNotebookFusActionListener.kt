// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.statistics.fus

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.core.impl.actions.NotebookRunAllAction
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.core.impl.file.notebook
import com.intellij.jupyter.core.jupyter.actions.JupyterRestartKernelAction
import com.intellij.jupyter.core.jupyter.actions.JupyterRestartKernelClearOutputsAction
import com.intellij.jupyter.core.jupyter.actions.JupyterRestartKernelRunAllAction
import com.intellij.jupyter.core.jupyter.editor.getJupyterVirtualFile

class KotlinNotebookFusActionListener: AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        val project = event.project ?: return

        fun getBackedFile(): BackedNotebookVirtualFile? {
            val backedFile = event.getJupyterVirtualFile() ?: return null
            return backedFile.takeIf { it.file.isKotlinNotebook }
        }

        when(action) {
            is JupyterRestartKernelAction,
            is JupyterRestartKernelRunAllAction,
            is JupyterRestartKernelClearOutputsAction -> {
                val backedFile = getBackedFile() ?: return
                val compilerService = JupyterCompilerService.getForFile(project, backedFile)
                val cellCountBeforeRestart = compilerService.executedCellsCount
                val classpathSizeBeforeRestart = compilerService.currentClasspath.size
                KotlinNotebookFeatureUsagesCollector.registerKernelRestart(project, cellCountBeforeRestart, classpathSizeBeforeRestart)
            }
            is NotebookRunAllAction -> {
                val backedFile = getBackedFile() ?: return
                val cellCountToRun = backedFile.notebook.computeCells().size
                KotlinNotebookFeatureUsagesCollector.registerRunAllCells(project, cellCountToRun)
            }
        }
    }
}
