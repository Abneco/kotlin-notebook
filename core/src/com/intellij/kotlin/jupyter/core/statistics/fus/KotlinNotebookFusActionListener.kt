// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.statistics.fus

import com.intellij.jupyter.core.core.impl.actions.run.NotebookRunAllAction
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.action.JupyterRestartKernelAction
import com.intellij.jupyter.core.jupyter.editor.getJupyterVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener

class KotlinNotebookFusActionListener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        val project = event.project ?: return

        fun getBackedFile(): BackedNotebookVirtualFile? {
            val backedFile = event.getJupyterVirtualFile() ?: return null
            return backedFile.takeIf { it.file.isKotlinNotebook }
        }

        when (action) {
            is JupyterRestartKernelAction -> {
                val backedFile = getBackedFile() ?: return
                val compilerService = JupyterCompilerService.getForFile(project, backedFile)
                val cellCountBeforeRestart = compilerService.executedCellsCount
                val classpathSizeBeforeRestart = compilerService.currentClasspath.size
                KotlinNotebookFeatureUsagesCollector.registerKernelRestart(project, cellCountBeforeRestart, classpathSizeBeforeRestart)
            }
            is NotebookRunAllAction -> {
                val backedFile = getBackedFile() ?: return
                val cellCountToRun = backedFile.notebookOrNull?.cellsCount() ?: -1
                KotlinNotebookFeatureUsagesCollector.registerRunAllCells(project, cellCountToRun)
            }
        }
    }
}
