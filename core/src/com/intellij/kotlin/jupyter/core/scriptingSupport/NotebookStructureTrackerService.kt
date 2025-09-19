// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.execution.listeners.NotebookSessionEventListener
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class NotebookStructureTrackerService(
    project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookStructurePerFileTracker>(project, coroutineScope) {

    init {
        project.messageBus.connect(this).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun sessionStarted(virtualFile: BackedNotebookVirtualFile) {
                    getOrCreate(virtualFile).clearData()
                }
            }
        )
    }

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): NotebookStructurePerFileTracker {
        return NotebookStructurePerFileTracker(project, virtualFile, fileScope)
    }

    companion object {
        fun getInstance(project: Project): NotebookStructureTrackerService = project.service<NotebookStructureTrackerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookStructurePerFileTracker {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}