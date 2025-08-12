// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Main service responsible for applying special highlighting for the Notebooks.
 * Child instances of the service track that cells are needed to be highlighted and keep track of
 * [com.intellij.openapi.editor.markup.RangeHighlighter] being applied to the [editor].
 */
@Service(Service.Level.PROJECT)
class NotebookHighlightingService(
    project: Project, coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookHighlightingFileManager>(project, coroutineScope) {
    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): NotebookHighlightingFileManager {
        return NotebookHighlightingFileManager(
            project,
            virtualFile,
            fileScope
        )
    }

    companion object {
        const val HL_DELAY_PAUSE: Long = 300
        fun getInstance(project: Project): NotebookHighlightingService = project.service()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingFileManager {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}

