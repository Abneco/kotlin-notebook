// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.getOrCreateKotlinNotebookToolWindow
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.toNotebookToolWindowPanelHelpId
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toAbsolutePath
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

/**
 * [KotlinNotebookSessionVariablesService] manages all variables things across the project:
 *  - mapping from notebooks to file services [NotebookVariablesPerFileStateService]
 *  - keeping KotlinNotebookToolWindow aligned with opened file in the Editor
 *
 */
@Service(Service.Level.PROJECT)
class KotlinNotebookSessionVariablesService(
    private val project: Project,
    coroutineScope: CoroutineScope
): NotebookProjectLevelService<NotebookVariablesPerFileStateService>(coroutineScope) {
    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            KotlinFileEditorManagerListener()
        )
    }

    private inner class KotlinFileEditorManagerListener : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            if (project.isDisposed || !project.isInitialized) return

            if (!event.newFile.isKotlinNotebook) return
            val virtualFile = event.newFile?.toBackedNotebookFile() ?: return

            refreshToolWindowIfPossible(virtualFile)
        }
    }

    private fun refreshToolWindowIfPossible(virtualFile: BackedNotebookVirtualFile) {
        coroutineScope.launch {
            val fileId = virtualFile.file.toAbsolutePath().toNotebookToolWindowPanelHelpId()

            withContext(Dispatchers.EDT) {
                val panel = getOrCreateKotlinNotebookToolWindow(project)
                val contentManager = panel.contentManager
                contentManager.contents.firstOrNull {
                    it.helpId == fileId && !it.isSelected
                }?.let {
                    contentManager.setSelectedContent(it)
                }
            }
        }
    }


    override fun createInstance(virtualFile: BackedNotebookVirtualFile, childScope: CoroutineScope): NotebookVariablesPerFileStateService {
        return NotebookVariablesPerFileStateService(
            project,
            virtualFile,
            childScope,
            this
        )
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookSessionVariablesService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookVariablesPerFileStateService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}