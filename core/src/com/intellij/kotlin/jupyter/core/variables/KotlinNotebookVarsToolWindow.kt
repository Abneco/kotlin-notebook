// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.variables

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.jupyter.core.jupyter.variables.common.JupyterVarsToolWindowPanel
import com.intellij.kotlin.jupyter.core.debug.KotlinNotebookDebugEditorsProvider
import com.intellij.kotlin.jupyter.core.debug.frame.KotlinNotebookVariablesFrame
import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.debug.util.shouldShowNotebookVariables
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClickListener
import com.intellij.ui.ListenerUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.content.Content
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView
import com.intellij.xdebugger.impl.frame.asProxy
import java.awt.BorderLayout
import java.awt.event.MouseEvent

class KotlinNotebookVarsToolWindow(
    project: Project,
    notebookFile: BackedNotebookVirtualFile,
    internal val panelSetupData: NotebookVariablesToolWindowSetup
) : JupyterVarsToolWindowPanel(project, notebookFile), UiDataProvider {
    private inner class VariablesListener: JupyterEnvironmentUpdateListener {
        override fun onJupyterEnvironmentUpdated(backedNotebookVirtualFile: BackedNotebookVirtualFile, values: XValueChildrenList?) {
            if (notebookFile != backedNotebookVirtualFile) return

            KotlinNotebookPluginScope.invokeOnEDT {
                rebuildView()
            }
        }
    }

    init {
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        project.messageBus.connect(this).subscribe(JupyterEnvironmentUpdateListener.TOPIC, VariablesListener())
    }

    override val shouldBeAddedOnTopLevel: Boolean = false

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
    override fun updateVariablesView() {
        variablesView?.rebuildView()
    }

    override fun createPanelContent(): Content {
        return createContent()
    }

    fun createContent(): Content {
        val panel = this
        val data = panelSetupData

        with(data) {
            return uiRunnerLayoutUi
                .createContent(
                    // provide unique id for each UI tab
                    helpId + title,
                    panel,
                    title,
                    null,
                    panel.getPreferredFocusedComponent()
                ).apply { isCloseable = false }
        }
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        val session = KotlinNotebookDebugSessionManager.getForFile(project, notebookFile).currentXSession ?: return
        sink[XDebugSessionProxy.DEBUG_SESSION_PROXY_KEY] = session.asProxy()
    }
}
