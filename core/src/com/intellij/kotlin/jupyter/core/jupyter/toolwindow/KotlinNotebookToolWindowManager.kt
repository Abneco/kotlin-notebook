// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.toolwindow

import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowManager.Companion.KOTLIN_NOTEBOOK_RUNNER_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import icons.KotlinJupyterIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
    internal fun getOrCreateKotlinNotebookToolWindow(): ToolWindow {
        return ToolWindowManager.getInstance(project).getToolWindow(KOTLIN_NOTEBOOK_TOOL_WINDOW_ID)
            ?: createKotlinNotebookToolWindow()
    }

    @RequiresEdt
    private fun createKotlinNotebookToolWindow(): ToolWindow {
        return ToolWindowManager.getInstance(project)
            .registerToolWindow(
                RegisterToolWindowTask(
                    KOTLIN_NOTEBOOK_TOOL_WINDOW_ID,
                    canCloseContent = true,
                    anchor = ToolWindowAnchor.BOTTOM
                )
            )
            .apply {
                setIcon(KotlinJupyterIcons.ToolWindowIcon)
                isAutoHide = false
            }
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