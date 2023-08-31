// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.variables

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ClickListener
import com.intellij.ui.ListenerUtil
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView
import org.jetbrains.kotlinx.jupyter.plugin.debug.KJupyterDebugEditorsProvider
import org.jetbrains.kotlinx.jupyter.plugin.debug.frame.KotlinNotebookVariablesFrame
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KJupyterDebugSessionManager
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowPanel
import org.jetbrains.plugins.notebooks.jupyter.variables.inline.JupyterInlineCallback
import java.awt.BorderLayout
import java.awt.event.MouseEvent

class JupyterKotlinVarsToolWindow(project: Project, notebookFile: BackedNotebookVirtualFile) : JupyterVarsToolWindowPanel(project, notebookFile) {
    private val session: JupyterNotebookSession? = JupyterRuntimeService.getInstance(project).getSession(notebookFile.file)

    override fun initVariablesView(frameVarsCallback: JupyterInlineCallback?) {
        removeAll()
        val debugManager = KJupyterDebugSessionManager.getForFile(project, notebookFile)

        val stackFrame = KotlinNotebookVariablesFrame(project, null, debugManager)
        removeClickListener()
        variablesView = XStandaloneVariablesView(project, KJupyterDebugEditorsProvider(), stackFrame)
        variablesView?.let {
            Disposer.register(this, it)
            add(it.panel, BorderLayout.CENTER)
            clickListener = object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    logViewUsage()
                    return false
                }
            }
            ListenerUtil.addClickListener(it.panel, clickListener)
        }

        Disposer.register(this, variablesView!!)
        add(variablesView!!.panel, BorderLayout.CENTER)

        validate()
        repaint()
    }
}


