// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.scratch.ScratchFileActions
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.kotlin.jupyter.core.language.FILE_TEMPLATE_KEY
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.IntellijInternalApi
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

fun getDefaultKotlinNotebookProjectPath(): Path {
    return Path.of(System.getProperty("user.home"), "ktnb").apply {
        createDirectories()
    }
}
