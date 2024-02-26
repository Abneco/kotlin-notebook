// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

abstract class StopKotlinKernelActionBase : DumbAwareAction(
    KotlinNotebookBundle.message("kotlin.jupyter.toolbar.actions.stop"),
    null,
    AllIcons.Debugger.KillProcess
)

class StopKotlinKernelAction(private val handler: KotlinKernelRunnableHandler): StopKotlinKernelActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        handler.stopKernel()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = handler.canStopKernel()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
