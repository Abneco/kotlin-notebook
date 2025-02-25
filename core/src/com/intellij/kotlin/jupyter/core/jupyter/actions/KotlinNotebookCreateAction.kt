// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.displayName
import com.intellij.kotlin.jupyter.core.language.templateName
import com.intellij.kotlin.jupyter.core.projectWizard.kotlinNotebookWelcomeFeaturesEnabled
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import icons.KotlinJupyterIcons

class KotlinNotebookCreateAction : CreateFileFromTemplateAction(), DumbAware {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(KotlinNotebookBundle.message("kotlin.jupyter.action.create.notebook.dialog.title"))
            .addAllTemplates()
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        KotlinNotebookBundle.message("kotlin.jupyter.action.create.notebook.name", templateName)

    public override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
        return CreateNotebookFactory.createFileFromTemplate(name, template, dir, defaultTemplateProperty)
    }

    private fun CreateFileFromTemplateDialog.Builder.addAllTemplates() {
        if (kotlinNotebookWelcomeFeaturesEnabled) {
            for (template in NotebookTemplate.entries) {
                addTemplate(template)
            }
        } else {
            addTemplate(NotebookTemplate.EMPTY)
        }
    }

    private fun CreateFileFromTemplateDialog.Builder.addTemplate(template: NotebookTemplate) {
        addKind(
            template.displayName,
            KotlinJupyterIcons.FileIcon,
            template.templateName
        )
    }
}
