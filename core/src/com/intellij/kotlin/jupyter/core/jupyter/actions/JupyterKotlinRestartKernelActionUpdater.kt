// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.jupyter.core.jupyter.JupyterBundle
import com.intellij.jupyter.core.jupyter.connections.action.getJupyterNotebookRuntimeSettings
import com.intellij.jupyter.core.jupyter.connections.execution.getJupyterServer
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterNotebookOfflineSettings
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterNotebookSessionSettings
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.ManagedJupyterServerNotebookSessionSettings
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerFinished
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerStarted
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerStarting
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerStopped
import com.intellij.jupyter.core.jupyter.helper.jupyterNotebookFile
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.notebooks.jupyter.core.icons.JupyterCoreIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.BadgeDotProvider
import com.intellij.ui.BadgeIcon
import com.intellij.util.ui.JBUI
import java.lang.invoke.MethodHandles

object JupyterKotlinRestartKernelActionUpdater {
    val LOG: Logger = Logger.getInstance(MethodHandles.lookup().lookupClass())

    fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
        if (e.dataContext.jupyterNotebookFile?.isKotlinNotebook != true) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        checkKernelAvailable(e)
        e.presentation.icon = JupyterCoreIcons.RestartKernel

        if (!e.presentation.isEnabled) return
        val fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR) ?: return
        when (val dependenciesStatus = JupyterNotebookDependencies.getStatus(fileEditor)) {
            JupyterNotebookDependencies.Status.UpToDate -> return
            is JupyterNotebookDependencies.Status.NotUpToDate -> {
                e.presentation.icon = BadgeIcon(
                    icon = JupyterCoreIcons.RestartKernel,
                    paint = JBUI.CurrentTheme.IconBadge.INFORMATION,
                    provider = badgeDotProvider()
                )

                e.presentation.text =
                    JupyterBundle.message("action.JupyterRestartKernelAction.text") + "<br><br>" + dependenciesStatus.message
            }
        }
    }

    private fun badgeDotProvider(): BadgeDotProvider {
        // We are putting the badge on the opposite side because otherwise it covers icon details
        val defaultX = BadgeDotProvider().x
        return BadgeDotProvider(x = 1 - defaultX)
    }

    private fun checkKernelAvailable(e: AnActionEvent) {
        checkKernelAvailable(e) {
            e.presentation.isEnabled = it
            if (e.place == ActionPlaces.PROJECT_VIEW_POPUP) {
                e.presentation.isVisible = it
            }
        }
    }

    private fun checkKernelAvailable(e: AnActionEvent, callback: (Boolean) -> Unit) {
        val project = e.project ?: return
        val virtualFile = e.notebookFile?.file ?: return
        val runtimeSettings = e.getJupyterNotebookRuntimeSettings()
        if (runtimeSettings == null) {
            val kernelSpecs = getJupyterServer(project, virtualFile)?.kernelSpecs
            callback(!kernelSpecs.isNullOrEmpty())
        } else {
            fun serverIsOffline() = callback(false)
            fun serverIsOnline() = callback(true)

            when (runtimeSettings) {
                is JupyterNotebookOfflineSettings -> serverIsOffline()
                is JupyterNotebookSessionSettings -> serverIsOnline()
                is ManagedJupyterServerNotebookSessionSettings -> when (runtimeSettings.jupyterServerExecution.state) {
                    JupyterServerFinished,
                    JupyterServerStarting,
                    JupyterServerStopped,
                        -> serverIsOffline()
                    is JupyterServerStarted -> serverIsOnline()
                }
            }
        }
    }
}