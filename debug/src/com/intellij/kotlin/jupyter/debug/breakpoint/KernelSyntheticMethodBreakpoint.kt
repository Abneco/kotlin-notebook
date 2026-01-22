// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.breakpoint

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.ui.breakpoints.SyntheticLineBreakpoint
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.openapi.project.Project
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.LocatableEvent

class KernelSyntheticMethodBreakpoint(
    project: Project,
    private val className: String,
    private val methodName: String,
    private val methodLineNumber: Int,
    private val eventHandler: (SuspendContextCommandImpl, LocatableEvent?) -> Unit
) : SyntheticLineBreakpoint(project) {
    companion object {
        private val LOG = notebookLogger()
    }

    init {
        suspendPolicy = DebuggerSettings.SUSPEND_THREAD
    }

    override fun getLineIndex(): Int {
        return methodLineNumber
    }

    override fun createRequest(debugProcess: DebugProcessImpl) {
        val targetClass = VirtualMachineProxyImpl.getCurrent().classesByNameProvider.get(className).singleOrNull()
        if (targetClass == null) {
            LOG.warn("Main class is not yet loaded :$className!")
        }

        createOrWaitPrepare(debugProcess, className)
    }

    override fun createOrWaitPrepare(debugProcess: DebugProcessImpl, classToBeLoaded: String) {
        debugProcess.requestsManager.callbackOnPrepareClasses(this, classToBeLoaded)
        val virtualMachineProxy = VirtualMachineProxyImpl.getCurrent()
        if (virtualMachineProxy.canBeModified()) {
            virtualMachineProxy.classesByName(classToBeLoaded).filter { it.isPrepared }.forEach {
                processClassPrepare(debugProcess, it)
            }
        }
    }

    override fun processClassPrepare(debugProcess: DebugProcess?, classType: ReferenceType) {
        if (classType.name() != className) return
        super.processClassPrepare(debugProcess, classType)
    }

    override fun createRequestForPreparedClass(debugProcess: DebugProcessImpl, classType: ReferenceType) {
        DebuggerUtilsEx.declaredMethodsByName(classType, methodName).forEach {
            createMethodRequest(debugProcess, it)
        }
    }

     private fun createMethodRequest(debugProcess: DebugProcessImpl, method: Method) {
         try {
             val location = method.locationOfCodeIndex(methodLineNumber.toLong())
             if (location == null) {
                 LOG.warn("Can't find location in method to set up a breakpoint in :$methodName")
                 return
             }
             val request = debugProcess.requestsManager.createBreakpointRequest(this, location)
             debugProcess.requestsManager.enableRequest(request)
         } catch (ex: AbsentInformationException) {
             LOG.warn("Failure during setting up method request", ex)
         }
    }

    override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
        eventHandler(action, event)
        return super.processLocatableEvent(action, event)
    }
}