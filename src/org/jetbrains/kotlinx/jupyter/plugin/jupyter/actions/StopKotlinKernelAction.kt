// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlinx.jupyter.plugin.i18n.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelProcessHandler

abstract class StopKotlinKernelActionBase : DumbAwareAction(
    JupyterKotlinBundle.message("kotlin.jupyter.toolbar.actions.stop"),
    null,
    AllIcons.Actions.Suspend
)

class StopKotlinKernelAction(private val handler: KotlinKernelProcessHandler): StopKotlinKernelActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        handler.destroyProcess()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !handler.isProcessTerminated && !handler.isProcessTerminating
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}