// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.editor.appearance.KotlinNotebookToolWindowBuilder
import java.nio.file.Path

private const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
private const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"


internal fun Path.toNotebookToolWindowPanelHelpId(): String {
    return KOTLIN_NOTEBOOK_RUNNER_ID + this.toAbsolutePath()
}

@RequiresEdt
fun showKotlinNotebookServerManagementToolWindow(
  mode: KotlinNotebookToolWindowRunMode,
) {
    val project = mode.project
    val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow(project)

    val id = mode.notebookPath.toNotebookToolWindowPanelHelpId()
    val notebookToolWindowBuilder = KotlinNotebookToolWindowBuilder(mode, id)

    val manager = toolWindow.contentManager

    val oldContent = manager.findContent(notebookToolWindowBuilder.windowTitle)
    if (oldContent != null) {
        manager.removeContent(oldContent, true)
    }

    val newContent = notebookToolWindowBuilder.createMainContent()
    manager.addContent(newContent, -1)
    manager.setSelectedContent(newContent)

    mode.makeToolWindowClosableWhenStoppingKernel(newContent)
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
