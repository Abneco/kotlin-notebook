// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level service that manages debug-aware cell execution for Notebooks.
 */
@Service(Service.Level.PROJECT)
internal class KotlinNotebookDebugAwareCellExecutorService(
    project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookDebugCellExecutorPerFileService>(project, coroutineScope) {

    override fun createInstance(
        virtualFile: BackedNotebookVirtualFile,
        fileScope: CoroutineScope
    ): NotebookDebugCellExecutorPerFileService {
        return NotebookDebugCellExecutorPerFileService(project, virtualFile, fileScope)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookDebugAwareCellExecutorService = project.service()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookDebugCellExecutorPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
