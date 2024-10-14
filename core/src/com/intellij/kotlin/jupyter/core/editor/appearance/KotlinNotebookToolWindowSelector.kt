// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.variables.common.JupyterNotebookToolWindowSelector
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowManager
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

class KotlinNotebookToolWindowSelector : JupyterNotebookToolWindowSelector {
    override fun getAvailableToolWindow(project: Project, virtualFile: BackedNotebookVirtualFile): ToolWindow? {
        return if (virtualFile.file.isKotlinNotebook) {
            KotlinNotebookToolWindowManager.getInstance(project)
                .getOrCreateKotlinNotebookToolWindow()
        } else null
    }
}