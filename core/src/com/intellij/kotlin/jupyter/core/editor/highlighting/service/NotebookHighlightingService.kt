// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope

/**
 * docs
 */
@Service(Service.Level.PROJECT)
class NotebookHighlightingService(
    project: Project, coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookHighlightingManager>(project, coroutineScope) {

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): NotebookHighlightingManager {
        // todo: localize read access, do not pollute it
        return withReadAccess {
            NotebookHighlightingManager(
                project,
                virtualFile,
                fileScope,
                null
            )
        }
    }

    companion object {
        const val HL_DELAY_PAUSE: Long = 300
        fun getInstance(project: Project): NotebookHighlightingService = project.service()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
            return getInstance(project).getOrCreate(virtualFile)
        }

        fun VirtualFile?.getHighlightingManagerForFile(project: Project): NotebookHighlightingManager? {
            return this?.let(BackedNotebookVirtualFile::takeIfBacked)?.let { getForFile(project, it) }
        }
    }
}

