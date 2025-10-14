// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.jupyter.core.jupyter.JupyterBundle
import com.intellij.jupyter.core.jupyter.connections.action.notebookSession
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

object KotlinNotebookRestartKernelActionUpdater {
    val LOG: Logger = Logger.getInstance(MethodHandles.lookup().lookupClass())

    fun update(e: AnActionEvent) {
        if (e.notebookFile?.isKotlinNotebook != true) {
            return
        }

        checkKernelAvailable(e)
        e.presentation.icon = JupyterCoreIcons.RestartKernel

        if (!e.presentation.isEnabled) return
        val fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR) ?: return
        when (val dependenciesStatus = KotlinNotebookRestartNotification.getStatus(fileEditor)) {
            KotlinNotebookRestartStatus.NotNeeded -> return
            is KotlinNotebookRestartStatus.Needed -> {
                e.presentation.icon = BadgeIcon(
                    icon = JupyterCoreIcons.RestartKernel,
                    paint = JBUI.CurrentTheme.IconBadge.INFORMATION,
                    provider = badgeDotProvider()
                )

                @Suppress("HardCodedStringLiteral")
                e.presentation.text =
                    buildString {
                        append(JupyterBundle.message("action.JupyterRestartKernelAction.text"))
                        append("<br><br>")
                        append(dependenciesStatus.message)
                    }
            }
        }
    }

    private fun badgeDotProvider(): BadgeDotProvider {
        // We are putting the badge on the opposite side because otherwise it covers icon details
        val defaultX = BadgeDotProvider().x
        return BadgeDotProvider(x = 1 - defaultX)
    }

    private fun checkKernelAvailable(e: AnActionEvent) {
        val notebookSession = e.notebookSession
        if (notebookSession == null) {
            e.presentation.isEnabledAndVisible = false
        }
        e.presentation.isEnabled = true
        if (e.place == ActionPlaces.PROJECT_VIEW_POPUP) {
            e.presentation.isVisible = true
        }
    }
}