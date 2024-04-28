// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.variables

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ClickListener
import com.intellij.ui.ListenerUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.content.Content
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView
import org.jetbrains.kotlinx.jupyter.plugin.debug.KotlinNotebookDebugEditorsProvider
import org.jetbrains.kotlinx.jupyter.plugin.debug.frame.KotlinNotebookVariablesFrame
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.shouldShowNotebookVariables
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowPanel
import java.awt.BorderLayout
import java.awt.event.MouseEvent

class KotlinNotebookVarsToolWindow(
    project: Project,
    notebookFile: BackedNotebookVirtualFile,
    private val panelSetupData: NotebookVariablesToolWindowSetup
) : JupyterVarsToolWindowPanel(project, notebookFile) {

    override fun getName() =
        KotlinNotebookBundle.message(
            "kotlin.jupyter.toolbar.tabs.variables"
        )

    override fun initVariablesView() {
        if (project.isDisposed || !project.shouldShowNotebookVariables) {
            showMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.default.message")
            )
            return
        }
        if (!panelSetupData.isEnabled) {
            showMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.not.enabled.message")
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

        // Overrides default context menu group for XVariablesViewBase, removing disabled actions
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

    override fun createContent(toolWindow: ToolWindow, index: Int): Content {
        return createContent()
    }

    fun createContent(): Content {
        val panel = this
        val data = panelSetupData

        with(data) {
            return uiRunnerLayoutUi
                .createContent(
                    id + title,
                    panel,
                    title,
                    null,
                    panel.getPreferredFocusedComponent()
                )
        }
    }
}
