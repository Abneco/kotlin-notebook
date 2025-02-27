// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.description
import com.intellij.kotlin.jupyter.core.language.displayName
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking


abstract class CreateKotlinNotebookFromTemplateAbstractAction(
    private val template: NotebookTemplate
) : AnAction(
    template.displayName,
    template.description,
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        createNotebook(template, e.project)
    }

    protected abstract fun createNotebook(template: NotebookTemplate, project: Project?)
}


@OptIn(IntellijInternalApi::class)
class CreateKotlinNotebookAndOpenProjectAction(template: NotebookTemplate) :
    CreateKotlinNotebookFromTemplateAbstractAction(template) {
    override fun createNotebook(template: NotebookTemplate, project: Project?) {
        runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("kotlin.notebook.create.project.progress"),
            TaskCancellation.cancellable()
        ) {
            val project = DefaultKotlinNotebookProject.getProject()
            createKotlinNotebookInProjectWhenProjectIsInitialized(project, template)
        }
    }
}


class CreateKotlinNotebookInCurrentProjectAction(template: NotebookTemplate) :
    CreateKotlinNotebookFromTemplateAbstractAction(template) {
    override fun createNotebook(template: NotebookTemplate, project: Project?) {
        if (project == null) return
        createKotlinNotebookInProjectWhenProjectIsInitialized(project, template)
    }
}
