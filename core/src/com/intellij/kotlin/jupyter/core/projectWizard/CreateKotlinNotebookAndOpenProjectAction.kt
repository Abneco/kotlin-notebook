// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.language.description
import com.intellij.kotlin.jupyter.core.language.displayName
import com.intellij.kotlin.jupyter.core.projectWizard.settings.NewNotebookOptions
import com.intellij.kotlin.jupyter.core.projectWizard.settings.getActualProjectPath
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking


abstract class CreateKotlinNotebookFromTemplateAbstractAction(
    protected val settings: NewNotebookOptions
) : AnAction(
    settings.template.displayName,
    settings.template.description,
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        createNotebook(e.project)
    }

    protected abstract fun createNotebook(project: Project?)
}

class CreateKotlinNotebookAndOpenProjectAction(settings: NewNotebookOptions) :
    CreateKotlinNotebookFromTemplateAbstractAction(settings) {
    override fun createNotebook(project: Project?) {
        runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("kotlin.notebook.create.project.progress"),
            TaskCancellation.cancellable()
        ) {
            val project = DefaultKotlinNotebookProject.getProject(settings.getActualProjectPath())
            createKotlinNotebookInProjectWhenProjectIsInitialized(
                project,
                settings.template,
                settings.notebookName,
            )
        }
    }
}
