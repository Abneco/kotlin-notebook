// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterDebugSessionManager
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterSessionPath
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowManager


class NotebookDebugProcessListener(
    private val project: Project,
    private val sessionPath: JupyterSessionPath,
    private val virtualFile: BackedNotebookVirtualFile,
    private val isSilent: Boolean = false
) : DebugProcessListener {
    companion object {
        private val LOG = thisLogger()
    }

    override fun paused(suspendContext: SuspendContext) {
        LOG.info("PAUSED")
        if (!isSilent) return
        runInEdt {
            JupyterVarsToolWindowManager.getInstance(project).updateVariablesView(virtualFile)
        }
    }

    override fun resumed(suspendContext: SuspendContext?) {
        //super.resumed(suspendContext)
    }

    override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        if (isSilent) return
        JupyterDebugSessionManager.getInstance(project).debugInSessionFinished(sessionPath)
        LOG.debug("Process terminated, closedByUser: ${closedByUser}")
    }

    override fun processAttached(process: DebugProcess) {
        LOG.info("Attached: ${process}")
    }
}