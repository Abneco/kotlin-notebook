// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.variables

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toAbsolutePath
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowPanel
import org.jetbrains.plugins.notebooks.jupyter.variables.common.NotebookVarsToolWindowPanelProvider

internal class NotebookVarsToolWindowProvider : NotebookVarsToolWindowPanelProvider {
    override fun isSupported(virtualFile: BackedNotebookVirtualFile): Boolean {
        return virtualFile.file.isKotlinNotebook && !ApplicationManager.getApplication().isUnitTestMode
    }

    override fun getToolWindowPanel(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterVarsToolWindowPanel {
        val variableService = KotlinNotebookSessionVariablesService.getForFile(project, virtualFile)
        if (!variableService.isToolWindowReady()) {
            error("Variable window was not created for: ${virtualFile.file.toAbsolutePath()}")
        }
        return variableService.getToolWindow()
    }
}
