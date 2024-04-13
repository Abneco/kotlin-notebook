// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.jupyter.server.ui.attachJupyterServerContentCloseListener
import java.nio.file.Path

private const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
private const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"


internal fun Path.toNotebookToolWindowPanelHelpId(): String {
    return KOTLIN_NOTEBOOK_RUNNER_ID + this
}

@RequiresEdt
fun showKotlinNotebookServerManagementToolWindow(
    handler: KotlinKernelProcessHandler,
) {
    val project = handler.project
    val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow(project)

    val id = handler.notebookPath.toNotebookToolWindowPanelHelpId()
    val notebookToolWindowBuilder = KotlinNotebookToolWindowBuilder(handler, id)

    val manager = toolWindow.contentManager

    val oldContent = manager.findContent(notebookToolWindowBuilder.windowTitle)
    if (oldContent != null) {
        manager.removeContent(oldContent, true)
    }

    val newContent = notebookToolWindowBuilder.createMainContent()
    manager.addContent(newContent, -1)
    manager.setSelectedContent(newContent)

    attachJupyterServerContentCloseListener(
        newContent,
        project,
        KotlinNotebookBundle.message("kotlin.jupyter.toolbar.session.name"),
        handler
    )

    handler.addKernelProcessListener(object : KotlinKernelProcessListener {
        override fun kernelTerminated(event: KotlinKernelProcessEvent) {
            if (!newContent.isValid || project.isDisposed || !project.isInitialized) return

            runInEdt {
                newContent.isCloseable = true
            }
        }
    })
}

@RequiresEdt
internal fun getOrCreateKotlinNotebookToolWindow(project: Project): ToolWindow {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID)
        ?: toolWindowManager.registerToolWindow(
            RegisterToolWindowTask(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID, canCloseContent = true, anchor = ToolWindowAnchor.BOTTOM)
        )
    toolWindow.setIcon(KotlinJupyterIcons.ToolWindowIcon)
    toolWindow.isAutoHide = false
    return toolWindow
}
