// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.variables

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClickListener
import com.intellij.ui.ListenerUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.content.Content
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView
import org.jetbrains.kotlinx.jupyter.plugin.debug.KotlinNotebookDebugEditorsProvider
import org.jetbrains.kotlinx.jupyter.plugin.debug.frame.KotlinNotebookVariablesFrame
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.isShouldShowNotebookVariables
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowPanel
import org.jetbrains.plugins.notebooks.jupyter.variables.inline.JupyterInlineCallback
import java.awt.BorderLayout
import java.awt.event.MouseEvent

class KotlinNotebookVarsToolWindow(
    project: Project,
    notebookFile: BackedNotebookVirtualFile,
    panelContent: ((JupyterVarsToolWindowPanel) -> Content)?
) : JupyterVarsToolWindowPanel(project, notebookFile) {
    private val session: JupyterNotebookSession? = JupyterRuntimeService.getInstance(project).getSession(notebookFile.file)
    @Volatile
    var panelContent: Content? = panelContent?.let { it(this) }

    override fun getName() =
        KotlinNotebookBundle.message(
            "kotlin.jupyter.toolbar.tabs.variables"
        )

    override fun initVariablesView(frameVarsCallback: JupyterInlineCallback?) {
        if (project.isDisposed || !project.isShouldShowNotebookVariables) {
            showMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.default.message")
            )
            return
        }
        removeAll()
        val debugManager = KotlinNotebookDebugSessionManager.getForFile(project, notebookFile)

        val stackFrame = KotlinNotebookVariablesFrame(project, null, debugManager)
        removeClickListener()
        variablesView = XStandaloneVariablesView(project, KotlinNotebookDebugEditorsProvider(), stackFrame)
        val viewReference = variablesView
        if (viewReference == null) return
        PopupHandler.installPopupMenu(viewReference.tree, "Notebook.XDebugger.StateValueGroup", "XDebuggerTreePopup")

        add(viewReference.panel, BorderLayout.CENTER)
        clickListener = object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                logViewUsage()
                return false
            }
        }
        ListenerUtil.addClickListener(viewReference.panel, clickListener)

        Disposer.register(this, viewReference)
        add(viewReference.panel, BorderLayout.CENTER)

        validate()
        repaint()
    }

    fun setPanelContent(content: Content) {
        this.panelContent = content
    }
}
