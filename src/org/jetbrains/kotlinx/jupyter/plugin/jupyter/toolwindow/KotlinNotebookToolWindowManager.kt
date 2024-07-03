// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
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
    private val stoppedSessions: MutableMap<Path, Content> = hashMapOf()

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
        val notebookPath = settings.notebookPath
        val runnableHandler = settings.handler

        stoppedSessions.remove(notebookPath)?.let { content ->
            removeContent(content)
        }

        val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow()

        val panelHelpId = notebookPath.toNotebookToolWindowPanelHelpId()
        val manager = toolWindow.contentManager
        if (project.isDisposed) return

        val notebookToolWindowBuilder = KotlinNotebookToolWindowBuilder(coroutineScope, settings, panelHelpId, manager)

        val newContent = notebookToolWindowBuilder.createMainContent()
        manager.addContent(newContent, -1)
        manager.setSelectedContent(newContent)

        runnableHandler.addKernelListener(object : KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                handleKernelTermination(notebookPath, newContent)
            }
        })

        registerContentInDisposer(runnableHandler, newContent)
        settings.toolWindowContentCreated(newContent)
    }

    private fun handleKernelTermination(notebookPath: Path, newContent: Content) {
        /**
         * The file panel will be closed when the new session is created for the same notebook.
         */
        stoppedSessions[notebookPath] = newContent

        /**
         * When the kernel is stopped, the file panel in the Kotlin Notebook tool window
         * should be made closable.
         * Otherwise, it should always be present.
         */
        if (!newContent.isValid || project.isDisposed || !project.isInitialized) return
        coroutineScope.launch(Dispatchers.EDT) {
            newContent.isCloseable = true
        }
    }

    /**
     * Registers content in the appropriate disposer based on the application mode.
     */
    private fun registerContentInDisposer(runnableHandler: KotlinKernelRunnableHandler, content: Content) {
        // In tests, Editor disposal assertion comes before project disposal
        val parentDisposable = if (ApplicationManager.getApplication().isUnitTestMode) {
            runnableHandler
        } else {
            KotlinNotebookSessionVariablesService.getInstance(project)
        }

        Disposer.register(parentDisposable, content)
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
        stoppedSessions.clear()
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