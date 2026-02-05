// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util.connection

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.SuspendContext
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionManager
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionPath
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.debug.breakpoint.kernel.KernelBreakpointController
import com.intellij.kotlin.jupyter.debug.listeners.NOTEBOOK_DEBUG_SESSION_TOPIC
import com.intellij.kotlin.jupyter.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.openapi.project.Project


internal class NotebookDebugProcessListener(
    private val project: Project,
    private val sessionPath: JupyterDebugSessionPath,
    private val virtualFile: BackedNotebookVirtualFile,
    private val breakpointController: KernelBreakpointController,
    private val isSilent: Boolean = false
) : DebugProcessListener {
    companion object {
        private val LOG = notebookLogger()
    }

    override fun paused(suspendContext: SuspendContext) {
        LOG.warn("PAUSED")
        if (!isSilent) return
        KotlinNotebookSessionVariablesService.getForFile(project, virtualFile).requestVariablesUpdate()
    }

    override fun resumed(suspendContext: SuspendContext?) {
        LOG.warn("RESUMED")
        //super.resumed(suspendContext)
    }

    override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        JupyterDebugSessionManager.getInstance(project).debugInSessionFinished(virtualFile)
        LOG.info("Process terminated, closedByUser: $closedByUser")

        // Notify listeners that the process has been detached and the port is released
        project.messageBus.syncPublisher(NOTEBOOK_DEBUG_SESSION_TOPIC).onProcessDetached(virtualFile)
        process.removeDebugProcessListener(this)
    }

    override fun processAttached(process: DebugProcess) {
        LOG.info("Attached: ${process}")
        if (process is DebugProcessImpl) {
            breakpointController
                .prepareInternalRequests(process)
        }
    }
}