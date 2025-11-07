// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.variables.presentation

import com.intellij.kotlin.jupyter.core.editor.appearance.KotlinNotebookToolWindowCoordinator
import com.intellij.kotlin.jupyter.core.editor.appearance.data.KotlinNotebookVariablesToolWindowConfiguration
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

/**
 * Class that manages the creation and disposal of [KotlinNotebookVarsToolWindow]s.
 *
 * This class is a property of [com.intellij.kotlin.jupyter.debug.variables.NotebookVariablesPerFileStateService], however,
 * since [KotlinNotebookVarsToolWindow] is required during whole notebook tool window setup, it should be
 * recreated on demand or changes.
 *
 * @see [KotlinNotebookToolWindowCoordinator]
 */
internal class KotlinNotebookToolVariablesWindowHandler : Disposable {
    private var notebookVariablesWindow: KotlinNotebookVarsToolWindow? = null

    @Synchronized
    fun getOrCreateToolWindow(
        providedSetupData: KotlinNotebookVariablesToolWindowConfiguration
    ): KotlinNotebookVarsToolWindow {
        val toolWindow = notebookVariablesWindow
        // we need to recreate a tool window if the provided setup data is different from the current
        if (toolWindow != null && providedSetupData == toolWindow.panelSetupData) {
            return toolWindow
        }
        val project = providedSetupData.project
        val virtualFile = providedSetupData.virtualFile

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
