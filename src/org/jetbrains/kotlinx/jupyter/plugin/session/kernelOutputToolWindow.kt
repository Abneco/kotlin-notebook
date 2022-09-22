// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.content.ContentFactory
import icons.KotlinJupyterIcons
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.actions.StopKotlinKernelAction
import org.jetbrains.plugins.notebooks.jupyter.server.ui.attachJupyterServerContentCloseListener

private const val KOTLIN_NOTEBOOK_TOOL_WINDOW_ID = "Kotlin Notebook"
private const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"

fun showKotlinNotebookServerManagementToolWindow(
    project: Project,
    handler: KotlinKernelProcessHandler,
) {
    val kernelContentTitle = JupyterKotlinBundle.message("kotlin.jupyter.toolbar.title", handler.notebookPath.fileName)
    val logContentTitle = JupyterKotlinBundle.message("kotlin.jupyter.toolbar.tabs.log", handler.notebookPath)

    val toolWindow: ToolWindow = getOrCreateKotlinNotebookToolWindow(project)

    val console = ConsoleViewImpl(project, GlobalSearchScope.allScope(project), true, true)

    console.attachToProcess(handler)

    val id = KOTLIN_NOTEBOOK_RUNNER_ID + handler.notebookPath.toString()
    val ui = RunnerLayoutUi.Factory.getInstance(project).create(id, kernelContentTitle, kernelContentTitle, project)
    val consoleContent = ui.createContent(id, console.component, logContentTitle, null, console.preferredFocusableComponent)
    consoleContent.isCloseable = false
    ui.addContent(consoleContent)

    val group = DefaultActionGroup(StopKotlinKernelAction(handler))
    ui.options.setLeftToolbar(group, ActionPlaces.TOOLBAR)

    val manager = toolWindow.contentManager
    val contentFactory = ContentFactory.getInstance()

    val oldContent = manager.findContent(kernelContentTitle)
    if (oldContent != null) {
        manager.removeContent(oldContent, true)
    }

    val newContent = contentFactory.createContent(ui.component, kernelContentTitle, true)
    newContent.isCloseable = false
    manager.addContent(newContent, -1)

    attachJupyterServerContentCloseListener(
        newContent,
        project,
        JupyterKotlinBundle.message("kotlin.jupyter.toolbar.session.name"),
        handler
    )
}

fun getOrCreateKotlinNotebookToolWindow(project: Project): ToolWindow {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID)
        ?: toolWindowManager.registerToolWindow(
            RegisterToolWindowTask(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID, canCloseContent = true, anchor = ToolWindowAnchor.BOTTOM)
        )
    toolWindow.setIcon(KotlinJupyterIcons.FileIconGrey)
    return toolWindow
}
