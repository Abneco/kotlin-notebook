// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareToggleAction
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.isAddProjectLibrariesToClasspath
import org.jetbrains.kotlinx.jupyter.plugin.settings.isBuildProject
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.editor.getJupyterVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook

abstract class KotlinJupyterToggleNotebookPropertyAction : DumbAwareToggleAction() {
    abstract var JupyterNotebook.property: Boolean

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (e.project == null || editor == null ||
            !editor.isKotlinNotebook || getJupyterVirtualFile(e) == null
        ) {
            e.presentation.isEnabledAndVisible = false
        }
        super.update(e)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val file = getJupyterVirtualFile(e) ?: return false
        return file.notebook.property
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val file = getJupyterVirtualFile(e) ?: return
        file.notebook.property = state
    }
}

class KotlinJupyterToggleBuildProjectAction : KotlinJupyterToggleNotebookPropertyAction() {
    override var JupyterNotebook.property
        get() = isBuildProject
        set(value) {
            isBuildProject = value
        }
}

class KotlinJupyterToggleAddProjectLibrariesAction : KotlinJupyterToggleNotebookPropertyAction() {
    override var JupyterNotebook.property
        get() = isAddProjectLibrariesToClasspath
        set(value) {
            isAddProjectLibrariesToClasspath = value
        }
}

class KotlinJupyterSettingsActions : DefaultActionGroup() {
    init {
        templatePresentation.isHideGroupIfEmpty = true
    }
}