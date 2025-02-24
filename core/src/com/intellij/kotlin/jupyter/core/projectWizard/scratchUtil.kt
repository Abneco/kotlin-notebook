// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.scratch.ScratchFileActions
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.kotlin.jupyter.core.language.FILE_TEMPLATE_KEY
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.nio.file.Path
import kotlin.io.path.createDirectories

@OptIn(IntellijInternalApi::class)
fun createScratchKotlinNotebookWhenProjectIsInitialized(
    project: Project,
    template: NotebookTemplate = NotebookTemplate.EMPTY,
    notebookNamePrefix: String = "notebook",
) {
    @Suppress("DEPRECATION")
    StartupManager.getInstance(project).runWhenProjectIsInitialized {
        val scratchContext = ScratchFileCreationHelper.Context().apply {
            language = JupyterKotlinFileType.language
            fileExtension = JupyterKotlinFileType.getDefaultExtension()
            filePrefix = notebookNamePrefix
        }
        val dataContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) {
            it[FILE_TEMPLATE_KEY] = template.getFileTemplate(project)
        }
        ScratchFileActions.doCreateNewScratch(project, scratchContext, dataContext)
    }
}

const val KOTLIN_NOTEBOOK_TEMPLATE_PROJECT_FOLDER_NAME: String = "KotlinNotebook"

fun getDefaultKotlinNotebookProjectPath(): Path {
    return Path.of(
        System.getProperty("user.home"),
        ".kotlinNotebook",
        KOTLIN_NOTEBOOK_TEMPLATE_PROJECT_FOLDER_NAME
    ).apply {
        createDirectories()
    }
}

fun getOrCreateDefaultKotlinNotebookProject(): Project {
    @Suppress("DialogTitleCapitalization")
    return runWithModalProgressBlocking(
        ModalTaskOwner.guess(),
        KotlinNotebookBundle.message("progress.title.opening.kotlin.notebook.project"),
        TaskCancellation.Companion.cancellable()
    ) {
        val projectPath = getDefaultKotlinNotebookProjectPath()

        // Mark project as trusted and open it
        TrustedProjects.setProjectTrusted(TrustedProjectsLocator.Companion.locateProject(projectPath, null), isTrusted = true)
        val project = ProjectManagerEx.Companion.getInstanceEx().openProjectAsync(projectPath, OpenProjectTask {
            runConfigurators = false
            isProjectCreatedWithWizard = true
        }) ?: error("Failed to open project")

        // Wait for project initialization
        @Suppress("DEPRECATION")
        StartupManager.getInstance(project).runWhenProjectIsInitialized {
            // Project is ready
        }

        project
    }
}
