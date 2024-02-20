// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.getOrCreateKotlinNotebookToolWindow
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterNotebookToolWindowSelector

class KotlinNotebookToolWindowSelector : JupyterNotebookToolWindowSelector {
    override fun getAvailableToolWindow(project: Project, virtualFile: BackedNotebookVirtualFile): ToolWindow? {
        return if (virtualFile.file.isKotlinNotebook) {
            getOrCreateKotlinNotebookToolWindow(project)
        } else null
    }
}