// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.actions.OpenFileAction
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.addRecentNotebook
import com.intellij.kotlin.jupyter.core.util.toAbsolutePath
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil

class OpenKotlinNotebookAction : OpenFileAction() {
    override fun createTemplatePresentation(): Presentation {
        return super.createTemplatePresentation().apply {
            text = KotlinNotebookBundle.message("action.OpenKotlinNotebookAction.text")
            putClientProperty(
                ActionUtil.COMPONENT_PROVIDER,
                WelcomeScreenActionsUtil.createToolbarTextButtonAction(this@OpenKotlinNotebookAction)
            )
        }
    }

    override fun createFileChooserDescriptor(project: Project?, fileToSelect: VirtualFile?): FileChooserDescriptor {
        return FileChooserDescriptorFactory
            .singleFile()
            .withExtensionFilter(
                KotlinNotebookBundle.message("kotlin.notebook.file.type.in.chooser"),
                JupyterFileType
            )
    }

    override suspend fun doOpenFile(project: Project?, virtualFile: VirtualFile) {
        val projectPath = if (virtualFile.isDirectory) {
            virtualFile
        } else {
            virtualFile.parent
        }

        val openedProject = DefaultKotlinNotebookProject.getProject(projectPath.toAbsolutePath())
        KotlinNotebookApplicationOptions.addRecentNotebook(
            RecentNotebook(
                virtualFile,
                projectPath
            )
        )

        super.doOpenFile(openedProject, virtualFile)
    }
}
