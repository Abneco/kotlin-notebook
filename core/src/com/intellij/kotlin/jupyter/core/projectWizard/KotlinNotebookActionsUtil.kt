package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsLocator.Companion.locateProject
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

object KotlinNotebookActionsUtil {
    fun getOrCreateKtnbProject(): Project {
        @Suppress("DialogTitleCapitalization")
        return runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("progress.title.opening.kotlin.notebook.project"),
            TaskCancellation.cancellable()
        ) {
            val projectPath = getDefaultKotlinNotebookProjectPath()

            // Mark project as trusted and open it
            TrustedProjects.setProjectTrusted(locateProject(projectPath, null), isTrusted = true)
            val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectPath, OpenProjectTask {
                runConfigurators = false
                isProjectCreatedWithWizard = true
            }) ?: error("Failed to open project")

            // Wait for project initialization
            StartupManager.getInstance(project).runWhenProjectIsInitialized {
                // Project is ready
            }

            project
        }
    }
}
