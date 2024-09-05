// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

@Service(Service.Level.PROJECT)
class NotebookStructureTrackerService(
    val project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookStructureClassTracker>(coroutineScope) {

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): NotebookStructureClassTracker {
        return NotebookStructureClassTracker(project, virtualFile, fileScope, this)
    }

    companion object {
        fun getInstance(project: Project) = project.service<NotebookStructureTrackerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookStructureClassTracker {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}