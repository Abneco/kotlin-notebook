// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelListener
import java.util.concurrent.ConcurrentHashMap


@Service(Service.Level.PROJECT)
class KotlinNotebookToolWindowManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {
    private val openedNotebooksToContent = ConcurrentHashMap<BackedNotebookVirtualFile, Content>()
    private val toolWindowContentManager = getOrCreateKotlinNotebookToolWindow(project).contentManager

    init {
      ApplicationManager.getApplication().messageBus.connect(this).subscribe(
          JupyterRestartKernelListener.TOPIC, JupyterRestartKernelListener { notebook ->
              if (project.isDisposed) return@JupyterRestartKernelListener
              val content = findContentForNotebook(notebook) ?: return@JupyterRestartKernelListener
              removeContent(notebook, content)
          }
      )
    }

    private fun removeContent(notebookVirtualFile: BackedNotebookVirtualFile, content: Content) {
        toolWindowContentManager.removeContent(content, true)
        openedNotebooksToContent.remove(notebookVirtualFile)
    }

    private fun findContentForNotebook(notebookVirtualFile: BackedNotebookVirtualFile): Content? {
        return if (notebookVirtualFile.file.isKotlinNotebook) {
            val stored = openedNotebooksToContent[notebookVirtualFile] ?: return null
            val inManager = toolWindowContentManager.findContent(stored.displayName)
            if (inManager == null) {
                openedNotebooksToContent.remove(notebookVirtualFile)
            }
            stored
        } else {
            null
        }
    }





    @RequiresEdt
    fun showKotlinNotebookServerManagementToolWindow(
        mode: KotlinNotebookToolWindowRunMode,
    ) {
        val project = mode.project
        val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow(project)

        val id = mode.notebookPath.toNotebookToolWindowPanelHelpId()
        val manager = toolWindow.contentManager
        if (project.isDisposed) return

        val notebookToolWindowBuilder = KotlinNotebookToolWindowBuilder(mode, id, manager)

        val newContent = notebookToolWindowBuilder.createMainContent()
        manager.addContent(newContent, -1)
        manager.setSelectedContent(newContent)
        openedNotebooksToContent[mode.notebookVirtualFile()] = newContent

        mode.makeToolWindowClosableWhenStoppingKernel(newContent)
    }


    override fun dispose() {
        coroutineScope.cancel()
        openedNotebooksToContent.clear()
    }

    companion object {


        fun getInstance(project: Project): KotlinNotebookToolWindowManager {
            return project.service<KotlinNotebookToolWindowManager>()
        }
    }
}