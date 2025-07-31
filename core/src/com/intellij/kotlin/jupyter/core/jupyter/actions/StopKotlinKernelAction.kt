// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.jupyter.core.jupyter.connections.action.shutdownNotebook
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class StopKotlinKernelActionBase : DumbAwareAction(
    KotlinNotebookBundle.message("kotlin.jupyter.toolbar.actions.stop"),
    null,
    AllIcons.Debugger.KillProcess
)

class StopKotlinKernelAction(
  private val project: Project,
  private val virtualFiles: List<VirtualFile>,
  private val editors: List<Editor>,
  private val handler: KernelRunnableHandler,
): StopKotlinKernelActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        shutdownNotebook(this::class, project, editors, virtualFiles)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = handler.canStopKernel()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
