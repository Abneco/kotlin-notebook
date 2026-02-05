// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.breakpoint

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level service that manages per-file breakpoint services.
 * Used to check if there are breakpoints in a notebook's dependent module.
 */
@Service(Service.Level.PROJECT)
class KotlinNotebookBreakpointsService(
    project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<KotlinNotebookBreakpointsPerFileService>(project, coroutineScope) {
    override fun createInstance(
        virtualFile: BackedNotebookVirtualFile,
        fileScope: CoroutineScope
    ): KotlinNotebookBreakpointsPerFileService {
        return KotlinNotebookBreakpointsPerFileService(project, virtualFile, fileScope)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookBreakpointsService = project.service()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): KotlinNotebookBreakpointsPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
