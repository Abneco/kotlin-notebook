// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import icons.KotlinJupyterIcons
import java.nio.file.Path

private const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
private const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"


internal fun Path.toNotebookToolWindowPanelHelpId(): String {
    return KOTLIN_NOTEBOOK_RUNNER_ID + this.toAbsolutePath()
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
