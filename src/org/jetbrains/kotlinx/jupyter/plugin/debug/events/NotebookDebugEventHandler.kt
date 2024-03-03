// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.events

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.openapi.project.Project
import com.sun.jdi.event.LocatableEvent
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.shouldShowNotebookVariables
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

interface NotebookDebugEventHandler {
    fun handleVMConnectEvent(virtualMachine: VirtualMachineProxy)
    fun handleVMDisconnectEvent(virtualMachine: VirtualMachineProxy)
    fun handleInternalDebugMethodEntryEvent(context: SuspendContext?, event: LocatableEvent?)
}


class NotebookDebugEventsHandler(
    private val project: Project,
    private val virtualFile: BackedNotebookVirtualFile
) : NotebookDebugEventHandler {
    override fun handleVMConnectEvent(virtualMachine: VirtualMachineProxy) {
        val process = virtualMachine.debugProcess
        if (process is DebugProcessImpl) {
            KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)
                .prepareInternalRequests(process)
        }
    }

    override fun handleVMDisconnectEvent(virtualMachine: VirtualMachineProxy) {

    }

    override fun handleInternalDebugMethodEntryEvent(context: SuspendContext?, event: LocatableEvent?) {
        if (context == null) return

        if (!project.shouldShowNotebookVariables) return
        KotlinNotebookSessionVariablesService.getForFile(project, virtualFile).requestVariablesUpdate()
    }
}