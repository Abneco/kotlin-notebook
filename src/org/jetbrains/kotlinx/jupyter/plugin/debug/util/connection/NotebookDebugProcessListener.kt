// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterDebugSessionManager
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterSessionPath


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
    }

    override fun resumed(suspendContext: SuspendContext?) {
        //super.resumed(suspendContext)
    }

    override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        JupyterDebugSessionManager.getInstance(project).debugInSessionFinished(sessionPath)
        LOG.info("Process terminated, closedByUser: ${closedByUser}")
    }

    override fun processAttached(process: DebugProcess) {
        LOG.info("Attached: ${process}")
    }
}