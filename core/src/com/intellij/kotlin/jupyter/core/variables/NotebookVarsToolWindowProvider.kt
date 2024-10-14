// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.variables

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.variables.common.JupyterVarsToolWindowPanel
import com.intellij.jupyter.core.jupyter.variables.common.NotebookVarsToolWindowPanelProvider
import com.intellij.kotlin.jupyter.core.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toAbsolutePath
import com.intellij.openapi.project.Project

internal class NotebookVarsToolWindowProvider : NotebookVarsToolWindowPanelProvider {
    override fun isSupported(virtualFile: BackedNotebookVirtualFile): Boolean {
        return virtualFile.file.isKotlinNotebook
    }

    override fun getToolWindowPanel(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterVarsToolWindowPanel {
        val variableService = KotlinNotebookSessionVariablesService.getForFile(project, virtualFile)
        if (!variableService.isToolWindowReady()) {
            error("Variable window was not created for: ${virtualFile.file.toAbsolutePath()}")
        }
        return variableService.getToolWindow()
    }
}
