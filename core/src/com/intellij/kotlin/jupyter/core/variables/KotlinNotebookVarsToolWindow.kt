// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.variables

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.helper.JupyterHelper
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.jupyter.core.jupyter.variables.common.JupyterVarsToolWindowUtils
import com.intellij.kotlin.jupyter.core.debug.KotlinNotebookDebugEditorsProvider
import com.intellij.kotlin.jupyter.core.debug.frame.KotlinNotebookVariablesFrame
import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.debug.util.debugFeaturesSupported
import com.intellij.kotlin.jupyter.core.debug.util.shouldFocusOnVariablesToolWindow
import com.intellij.kotlin.jupyter.core.debug.util.shouldShowNotebookVariables
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowManager
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.isCurrentlySelectedInEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.ui.ClickListener
import com.intellij.ui.ListenerUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.content.Content
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView
import com.intellij.xdebugger.impl.proxy.asProxy
import java.awt.BorderLayout
import java.awt.event.MouseEvent

class KotlinNotebookVarsToolWindow(
    val project: Project,
    val notebookFile: BackedNotebookVirtualFile,
    internal val panelSetupData: NotebookVariablesToolWindowSetup
) : SimpleToolWindowPanel(false), Disposable, UiDataProvider {
    private var variablesView: XStandaloneVariablesView? = null
    private var clickListener: ClickListener? = null

    init {
        subscribeToEvents()
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

    private inner class VariablesListener : JupyterEnvironmentUpdateListener {
        override fun onRuntimeEnvironmentUpdate(
            backedNotebookVirtualFile: BackedNotebookVirtualFile,
            values: XValueChildrenList?
        ) {
            if (notebookFile != backedNotebookVirtualFile || !project.shouldShowNotebookVariables) return

            KotlinNotebookPluginScope.invokeOnEDT {
                rebuildView()

                if (shouldFocusOnFile) {
                    requestFocusOnTab()
                }
            }
        }
    }

    private val shouldFocusOnFile: Boolean
        get() = project.shouldFocusOnVariablesToolWindow
                && notebookFile.debugFeaturesSupported(project)
                && notebookFile.isCurrentlySelectedInEditor(project)

    private val shouldUpdateVariablesList: Boolean
        get() = when {
            project.isDisposed || !project.shouldShowNotebookVariables -> {
                showMessage(
                    KotlinNotebookBundle.message("kotlin.jupyter.debug.node.default.message")
                )
                false
            }
            !notebookFile.debugFeaturesSupported(project) -> {
                showMessage(
                    KotlinNotebookBundle.message("kotlin.jupyter.debug.node.not.enabled.message")
                )
                false
            }
            else -> true
        }

    private fun rebuildView() {
        if (variablesView == null) {
            initVariablesView()
        } else {
            updateVariablesView()
        }
    }

    private fun requestFocusOnTab() {
        val runnerUi = panelSetupData.uiRunnerLayoutUi
        val contentManager = runnerUi.contentManager
        val variablesContent = runnerUi.contents.firstOrNull {
            it.tabName == panelSetupData.title
        }
        if (variablesContent == null || variablesContent.isSelected) return

        contentManager.setSelectedContent(variablesContent, true)

        val notebookToolWindow = KotlinNotebookToolWindowManager.getInstance(project).getOrCreateKotlinNotebookToolWindow()
        if (!notebookToolWindow.isActive) {
            notebookToolWindow.show()
        }
    }

    private fun subscribeToEvents() {
        project.messageBus.connect(this).subscribe(JupyterEnvironmentUpdateListener.TOPIC, VariablesListener())
    }

    private fun initVariablesView() {
        if (!shouldUpdateVariablesList) return
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
        clickListener = createClickListener()
        ListenerUtil.addClickListener(viewReference.panel, clickListener)

        Disposer.register(this, viewReference)
        add(viewReference.panel, BorderLayout.CENTER)

        validate()
        repaint()
    }

    private fun updateVariablesView() {
        if (!shouldUpdateVariablesList) return

        variablesView?.rebuildView()
    }


    private fun createClickListener(): ClickListener = object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            JupyterVarsToolWindowUtils.logViewUsage(project)
            return false
        }
    }

    private fun removeClickListener() {
        variablesView?.let { view ->
            clickListener?.let { ListenerUtil.removeClickListener(view.panel, it) }
        }
        clickListener = null
    }

    private fun showMessage(@NlsContexts.DialogMessage text: String) {
        removeAll()
        add(JupyterVarsToolWindowUtils.createPlaceholder(text))
        variablesView = null
        repaint()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[JupyterHelper.FORCED_NOTEBOOK] = notebookFile
        val session = KotlinNotebookDebugSessionManager.getForFile(project, notebookFile).currentXSession ?: return
        sink[XDebugSessionProxy.DEBUG_SESSION_PROXY_KEY] = session.asProxy()
    }

    override fun getName(): String = KotlinNotebookBundle.message(
        "kotlin.jupyter.toolbar.tabs.variables"
    )

    override fun dispose() {
        removeClickListener()
    }
}