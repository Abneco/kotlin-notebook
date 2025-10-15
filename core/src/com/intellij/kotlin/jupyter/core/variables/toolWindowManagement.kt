// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.variables

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.editor.appearance.KotlinNotebookToolWindowCoordinator
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe

/**
 * Class that manages the creation and disposal of [KotlinNotebookVarsToolWindow]s.
 *
 * This class is a property of [com.intellij.kotlin.jupyter.core.debug.variables.NotebookVariablesPerFileStateService], however,
 * since [KotlinNotebookVarsToolWindow] is required during whole notebook tool window setup, it should be
 * recreated on demand or changes.
 *
 * @see [KotlinNotebookToolWindowCoordinator]
 */
class KotlinNotebookToolWindowHandler : Disposable {
    private var notebookVariablesWindow: KotlinNotebookVarsToolWindow? = null

    val isToolWindowReady: Boolean
        get() = synchronized(this) {
            notebookVariablesWindow != null
        }

    @Synchronized
    fun getOrCreateToolWindow(
        project: Project,
        virtualFile: BackedNotebookVirtualFile,
        providedSetupData: NotebookVariablesToolWindowSetup?
    ): KotlinNotebookVarsToolWindow {
        val toolWindow = notebookVariablesWindow
        // we need to recreate a tool window if the provided setup data is different from the current
        if (toolWindow != null && providedSetupData == toolWindow.panelSetupData) {
            return toolWindow
        }

        if (providedSetupData == null) {
            throw IllegalArgumentException("No provided data for setting up ToolWindow for $virtualFile")
        }

        val newPanel = KotlinNotebookVarsToolWindow(project, virtualFile, providedSetupData)
        toolWindow?.disposeOf()
        notebookVariablesWindow = newPanel
        return newPanel
    }

    private fun KotlinNotebookVarsToolWindow?.disposeOf() {
        val window = this ?: return

        KotlinNotebookPluginScope.invokeOnEDT {
            Disposer.dispose(window)
        }
    }

    override fun dispose() {
        val window = notebookVariablesWindow ?: return
        window.disposeOf()
        notebookVariablesWindow = null
    }
}

data class NotebookVariablesToolWindowSetup(
    val uiRunnerLayoutUi: RunnerLayoutUi,
    val helpId: String,
    @NlsSafe val title: String
)
