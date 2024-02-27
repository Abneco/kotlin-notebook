// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.getOrCreateKotlinNotebookToolWindow
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.fileNameFromProjectRoot
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

@Service(Service.Level.PROJECT)
class KotlinNotebookSessionVariablesService(
    private val project: Project,
    coroutineScope: CoroutineScope
): NotebookProjectLevelService<NotebookVariablesPerFileState>(coroutineScope) {
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

            coroutineScope.launch {
                refreshToolWindowIfPossible(virtualFile)
            }
        }

        private suspend fun refreshToolWindowIfPossible(virtualFile: BackedNotebookVirtualFile) {
            val name = virtualFile.file.path.fileNameFromProjectRoot(project)
            withContext(Dispatchers.EDT) {
                val panel = getOrCreateKotlinNotebookToolWindow(project)
                val contentManager = panel.contentManager
                contentManager.contents.firstOrNull {
                    it.toolwindowTitle == name && !it.isSelected
                }?.let {
                    contentManager.setSelectedContent(it)
                }
            }
        }
    }


    override fun createInstance(virtualFile: BackedNotebookVirtualFile): NotebookVariablesPerFileState {
        return NotebookVariablesPerFileState(
            project,
            virtualFile,
            coroutineScope.childScope(),
            this
        )
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookSessionVariablesService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookVariablesPerFileState {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}