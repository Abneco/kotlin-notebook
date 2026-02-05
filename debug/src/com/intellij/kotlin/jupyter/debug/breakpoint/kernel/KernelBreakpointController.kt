// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.breakpoint.kernel

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.debug.events.NotebookDebugEventsHandler
import com.intellij.kotlin.jupyter.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesEnabled
import com.intellij.kotlin.jupyter.debug.util.runOnManagerThread
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Manages internal kernel breakpoints and
 * instrumentation needs for debugging features.
 *
 */
internal class KernelBreakpointController(
    project: Project,
    private val virtualFile: BackedNotebookVirtualFile,
    private val eventsHandler: NotebookDebugEventsHandler,
    private val onBreakpointHit: (EvaluationContextImpl) -> Unit
) {
    private val evaluationContextRef = AtomicReference<EvaluationContextImpl?>(null)

    val evaluationContext: EvaluationContextImpl?
        get() = evaluationContextRef.get()

    private val kernelThreadBreakpoint = KernelSyntheticMethodBreakpoint(
        project,
        KotlinNotebookSessionInternalNamesProvider.notebookClassName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugMethodName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugInsideMethodBreakpointLineNumber,
    ) { command, event ->
        val suspendContext = command.suspendContext
        if (suspendContext != null) {
            val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
            evaluationContextRef.set(evalContext)
            onBreakpointHit(evalContext)
        }
        eventsHandler.handleInternalDebugMethodEntryEvent(suspendContext, event)
    }

    fun prepareInternalRequests(debugProcess: DebugProcessImpl) {
        if (!debugFeaturesEnabled) return
        kernelThreadBreakpoint.createRequest(debugProcess)
    }

    fun clearContext() {
        evaluationContextRef.set(null)
    }

    /**
     * Executes the given [action] with a synthetic breakpoint disabled,
     * restoring the original state afterward.
     *
     * If the debug context is not available, [action] is executed without breakpoint manipulation.
     */
    @RequiresBackgroundThread
    @OptIn(ExperimentalContracts::class)
    suspend inline fun withNonSuspendingBreakpoint(
        debuggerSession: DebuggerSession?,
        action: suspend () -> Unit
    ) {
        contract {
            callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        }

        val process = debuggerSession?.process
        val evalContext = evaluationContext
        val suspendContext = evalContext?.suspendContext

        if (process == null || evalContext == null || suspendContext == null) {
            LOG.warn("Debug context not fully available for ${virtualFile.file.name}, executing block without breakpoint manipulation")
            action()
            return
        }

        val requestManager = process.requestsManager
        evalContext.resumeSuspendedContext(requestManager)

        try {
            action()
        }
        finally {
            evalContext.runOnManagerThread {
                kernelThreadBreakpoint.updateBreakpointEnablement(requestManager, true)
            }
        }
    }

    private suspend inline fun EvaluationContextImpl.resumeSuspendedContext(requestManager: RequestManagerImpl) {
        val process = debugProcess
        val managerThread = this.managerThread
        val command = process.createResumeCommand(suspendContext)

        runOnManagerThread {
            kernelThreadBreakpoint.updateBreakpointEnablement(requestManager, false)
        }
        managerThread.invokeAndWait(command)
    }

    companion object {
        private val LOG = notebookLogger()
    }
}