// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionManager
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterSessionPath


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
        LOG.warn("PAUSED")
        if (!isSilent) return
    }

    override fun resumed(suspendContext: SuspendContext?) {
        LOG.warn("RESUMED")
        //super.resumed(suspendContext)
    }

    override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        JupyterDebugSessionManager.getInstance(project).debugInSessionFinished(sessionPath)
        LOG.info("Process terminated, closedByUser: ${closedByUser}")
    }

    override fun processAttached(process: DebugProcess) {
        LOG.info("Attached: ${process}")
        if (process is DebugProcessImpl) {
            KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)
                .prepareInternalRequests(process)
        }
    }
}