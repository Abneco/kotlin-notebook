// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.Companion.locateProject
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

@OptIn(IntellijInternalApi::class)
class CreateKotlinNotebookAction(
    private val template: NotebookTemplate
) : AnAction(
    template.displayName,
    template.description,
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        createNotebook(template)
    }

    private fun createNotebook(template: NotebookTemplate) {
        runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("kotlin.notebook.create.project.progress"),
            TaskCancellation.cancellable()
        ) {
            // Create project directory
            val projectPath = getDefaultKotlinNotebookProjectPath()

            // Mark project as trusted and open it
            TrustedProjects.setProjectTrusted(locateProject(projectPath, null), isTrusted = true)
            val projectManager = ProjectManagerEx.getInstanceEx()
            val project = projectManager.openProjectAsync(projectPath, OpenProjectTask {
                runConfigurators = false
                isProjectCreatedWithWizard = true
            }) ?: return@runWithModalProgressBlocking

            createScratchKotlinNotebookWhenProjectIsInitialized(project, template)
        }
    }
}
