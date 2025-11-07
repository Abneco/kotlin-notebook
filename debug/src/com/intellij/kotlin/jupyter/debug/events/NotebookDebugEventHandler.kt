// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.events

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.util.shouldShowNotebookVariables
import com.intellij.kotlin.jupyter.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.openapi.project.Project
import com.sun.jdi.event.LocatableEvent

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