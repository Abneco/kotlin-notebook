// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowManager.Companion.KOTLIN_NOTEBOOK_RUNNER_ID
import java.nio.file.Path


internal fun Path.toNotebookToolWindowPanelHelpId(): String {
    return KOTLIN_NOTEBOOK_RUNNER_ID + this.toAbsolutePath()
}

@Service(Service.Level.PROJECT)
class KotlinNotebookToolWindowManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {

    @RequiresEdt
    private fun removeContent(content: Content) {
        getOrCreateKotlinNotebookToolWindow().contentManager.removeContent(content, true)
    }

    @CalledInAny
    fun showKotlinNotebookServerManagementToolWindow(
        settings: KotlinNotebookToolWindowSettings,
    ) {
        coroutineScope.launch(Dispatchers.EDT) {
            showKotlinNotebookServerManagementToolWindowImpl(settings)
        }
    }

    @RequiresEdt
    private suspend fun showKotlinNotebookServerManagementToolWindowImpl(settings: KotlinNotebookToolWindowSettings) {
        val project = settings.project

        val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow()

        val panelHelpId = settings.notebookPath.toNotebookToolWindowPanelHelpId()
        val manager = toolWindow.contentManager
        if (project.isDisposed) return

        val notebookToolWindowBuilder = KotlinNotebookToolWindowBuilder(coroutineScope, settings, panelHelpId, manager)

        val newContent = notebookToolWindowBuilder.createMainContent()
        manager.addContent(newContent, -1)
        manager.setSelectedContent(newContent)

        settings.handler.addKernelListener(object : KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                coroutineScope.launch(Dispatchers.EDT) {
                    removeContent(newContent)
                }
            }
        })

        settings.makeToolWindowClosableWhenStoppingKernel(newContent)
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
    }

    companion object {
        internal const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
        internal const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"

        fun getInstance(project: Project): KotlinNotebookToolWindowManager {
            return project.service<KotlinNotebookToolWindowManager>()
        }
    }
}