// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.variables

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile


class KotlinNotebookToolWindowHandler {
    private var variableWindowReference: KotlinNotebookVarsToolWindow? = null

    val isToolWindowReady: Boolean
        get() = synchronized(this) {
            variableWindowReference != null
        }

    @Synchronized
    fun getOrCreateToolWindow(
        project: Project,
        virtualFile: BackedNotebookVirtualFile,
        providedSetupData: NotebookVariablesToolWindowSetup?
    ): KotlinNotebookVarsToolWindow {
        val toolWindow = variableWindowReference
        if (toolWindow != null) {
            return toolWindow
        }

        if (providedSetupData == null) {
            throw IllegalArgumentException("No provided data for setting up ToolWindow for $virtualFile")
        }

        val newPanel = KotlinNotebookVarsToolWindow(project, virtualFile, providedSetupData)
        variableWindowReference = newPanel
        return newPanel
    }

    fun clear() {
        variableWindowReference = null
    }
}

data class NotebookVariablesToolWindowSetup(
    val uiRunnerLayoutUi: RunnerLayoutUi,
    val id: String,
    @NlsSafe val title: String,
    val isEnabled: Boolean,
)
