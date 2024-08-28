// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.jupyter.actions.shutdownNotebook

abstract class StopKotlinKernelActionBase : DumbAwareAction(
    KotlinNotebookBundle.message("kotlin.jupyter.toolbar.actions.stop"),
    null,
    AllIcons.Debugger.KillProcess
)

class StopKotlinKernelAction(
    private val project: Project,
    private val virtualFiles: List<VirtualFile>,
    private val editors: List<Editor>,
    private val handler: KotlinKernelRunnableHandler,
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
