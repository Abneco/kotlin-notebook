// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import icons.KotlinJupyterIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowManager.Companion.KOTLIN_NOTEBOOK_RUNNER_ID
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelListener
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


internal fun Path.toNotebookToolWindowPanelHelpId(): String {
    return KOTLIN_NOTEBOOK_RUNNER_ID + this.toAbsolutePath()
}

@Service(Service.Level.PROJECT)
class KotlinNotebookToolWindowManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {
    private val openedNotebooksToContent = ConcurrentHashMap<BackedNotebookVirtualFile, Content>()
    private val toolWindowContentManager = getOrCreateKotlinNotebookToolWindow().contentManager

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
        val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow()

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

    @RequiresEdt
    internal fun getOrCreateKotlinNotebookToolWindow(): ToolWindow {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID)
            ?: toolWindowManager.registerToolWindow(
                RegisterToolWindowTask(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID, canCloseContent = true, anchor = ToolWindowAnchor.BOTTOM)
            )
        toolWindow.setIcon(KotlinJupyterIcons.ToolWindowIcon)
        toolWindow.isAutoHide = false
        return toolWindow
    }


    override fun dispose() {
        coroutineScope.cancel()
        openedNotebooksToContent.clear()
    }

    companion object {
        internal const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
        internal const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"

        fun getInstance(project: Project): KotlinNotebookToolWindowManager {
            return project.service<KotlinNotebookToolWindowManager>()
        }
    }
}