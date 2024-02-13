// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

@Service(Service.Level.PROJECT)
class NotebookSessionVariablesService(
    private val project: Project,
    coroutineScope: CoroutineScope
): NotebookProjectLevelService<NotebookVariablesPerFileState>(coroutineScope) {
    override fun createInstance(virtualFile: BackedNotebookVirtualFile): NotebookVariablesPerFileState {
        return NotebookVariablesPerFileState(
            project,
            virtualFile,
            coroutineScope.childScope(),
            this
        )
    }

    companion object {
        fun getInstance(project: Project) = project.service<NotebookSessionVariablesService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookVariablesPerFileState {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}