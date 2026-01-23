// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.session

import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterDebugSessionPath
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.Companion.isIncomplete
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.runSafely
import com.intellij.kotlin.jupyter.debug.breakpoint.KernelSyntheticMethodBreakpoint
import com.intellij.kotlin.jupyter.debug.events.NotebookDebugEventsHandler
import com.intellij.kotlin.jupyter.debug.i18n.KotlinNotebookDebugBundle
import com.intellij.kotlin.jupyter.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.kotlin.jupyter.debug.util.connection.DebugConnectionUtility
import com.intellij.kotlin.jupyter.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import com.intellij.kotlin.jupyter.debug.util.connection.NotebookDebugProcessListener
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesEnabled
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

class KotlinNotebookFileDebugSession(
    public override val virtualFile: BackedNotebookVirtualFile,
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val portProvider: () -> Int?
): NotebookPerFileChildService(virtualFile, coroutineScope) {
    companion object {
        private val LOG = notebookLogger()
    }

    private val currentConfigRef: AtomicReference<DebugSessionConfig?> = AtomicReference(null)
    private val debuggerSessionRef = AtomicReference<DebuggerSession?>(null)
    private val sessionMutex = Mutex()

    private val evaluationContextRef = AtomicReference<EvaluationContextImpl?>(null)

    private val eventsHandler = NotebookDebugEventsHandler(project, virtualFile)

    init {
        project.initServiceListeners(this)

        val port = portProvider()
        if (port != null) {
            currentConfigRef.set(DebugSessionConfig(port))
        }
    }

    private fun Project.initServiceListeners(parentDisposable: Disposable) {
        val messageBus = messageBus
        messageBus.connect(parentDisposable).subscribe(
            NotebookScriptsStateListener.TOPIC,
            object : NotebookScriptsStateListener {
                override fun scriptsConfigurationUpdated(
                    file: BackedNotebookVirtualFile,
                    updateState: NotebookScriptsStateListener.UpdateState
                ) {
                    if (project.isDisposed) return
                    if (file != virtualFile || updateState.isIncomplete || debuggerSession?.isConnecting == true) return

                    messageBus.syncPublisher(JupyterEnvironmentUpdateListener.TOPIC)
                        .onRuntimeEnvironmentUpdate(virtualFile, null)
                }
            }
        )

        JupyterExecutionListener.register(parentDisposable, object : JupyterExecutionListener {
            override suspend fun sessionCreated(session: JupyterNotebookSession) {
                if (project.isDisposed) return
                if (session.virtualFile != virtualFile) return

                val port = targetDebugPort ?: provideFreshDebugPort() ?: return
                val debuggerSession = getOrCreateVmDebuggerSession(
                    DebugSessionConfig(port),
                    forceRestart = true
                )
                LOG.info("Debugger session after start: $debuggerSession")
            }
        })
    }

    private val kernelThreadBreakpoint = KernelSyntheticMethodBreakpoint(
        project,
        KotlinNotebookSessionInternalNamesProvider.notebookClassName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugMethodName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugInsideMethodBreakpointLineNumber,
    ) { command, event ->
        val suspendContext = command.suspendContext
        if (suspendContext != null) {
            evaluationContextRef.set(
                EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
            )
        }
        eventsHandler.handleInternalDebugMethodEntryEvent(suspendContext, event)
    }

    val currentStackFrameProxy: StackFrameProxyImpl?
        get() = debuggerSession?.process?.debuggerContext?.frameProxy

    val evaluationContext: EvaluationContextImpl?
        get() = evaluationContextRef.get()

    val targetDebugPort: Int? get() = currentConfigRef.get()?.port

    fun provideFreshDebugPort(): Int? {
        val port = portProvider() ?: return null
        currentConfigRef.set(DebugSessionConfig(port))
        return port
    }

    fun prepareInternalRequests(debugProcess: DebugProcessImpl) {
        if (!debugFeaturesEnabled) return

        kernelThreadBreakpoint.createRequest(debugProcess)
    }

    /**
     * Executes the given block with synthetic breakpoint policy set to SUSPEND_NONE.
     * The breakpoint's eventHandler will still be called, but execution won't stop.
     * Original policy is restored after block completes (or on exception).
     */
    suspend fun withNonSuspendingBreakpoint(block: suspend () -> Unit) {
        val original = kernelThreadBreakpoint.suspendPolicy
        try {
            kernelThreadBreakpoint.suspendPolicy = DebuggerSettings.SUSPEND_NONE
            block()
        }
        finally {
            kernelThreadBreakpoint.suspendPolicy = original
        }
    }

    val currentXSession: XDebugSession?
        get() = debuggerSessionRef.get()?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = debuggerSessionRef.get()

    val isLiveSession: Boolean
        get() = currentXSession != null

    private fun DebuggerSession.configureSessionAfterAttach(config: DebugSessionConfig) {
        addProcessListener(process, config.silent)

        if (!config.silent) {
            XDebuggerManagerImpl.getNotificationGroup().createNotification(
                KotlinNotebookDebugBundle.message("kotlin.jupyter.debug.support.text"), MessageType.INFO
            ).notify(project)
        }
    }

    // see JavaAttachDebuggerProvider
    internal suspend fun getOrCreateVmDebuggerSession(
        config: DebugSessionConfig,
        forceRestart: Boolean = false
    ): DebuggerSession? {
        if (checkSessionCanBeReused(forceRestart)) {
            return debuggerSession
        }

        sessionMutex.withLock {
            if (project.isDisposed) return null
            if (checkSessionCanBeReused(forceRestart)) {
                return debuggerSession
            }
            disposeCurrentSession()

            return createNewDebuggerSession(config).also { newSession ->
                if (newSession == null) {
                    clearDebugSessionData()
                    return null
                }
                currentConfigRef.set(config)
                debuggerSessionRef.set(newSession)
            }
        }
    }

    private fun checkSessionCanBeReused(forceRestart: Boolean): Boolean {
        if (!isLiveSession) return false

        return !forceRestart && debuggerSession?.isAttached == true
    }

    private suspend fun createNewDebuggerSession(config: DebugSessionConfig): DebuggerSession? {
        return try {
            DebuggerSettings.getInstance().transport = config.transport
            val environmentData = DebugConnectionUtility.buildDebugEnvironment(project, config, virtualFile)
                ?: return null

            val newSession = withContext(Dispatchers.EDT) {
                tryAttachToTargetVM(
                    environmentData.debugEnvironment,
                    environmentData.executionEnvironment,
                    config.silent
                )
            }
            if (newSession == null) {
                return null
            }

            // Might throw, but PCE is not expected from this logic
            runSafely({
                newSession.configureSessionAfterAttach(config)
            }, onFailure = {
                LOG.warn("Failed to configure debugger session", it)
                newSession.dispose()
                throw it
            })

            newSession
        } catch (ex: Exception) {
            LOG.error("Failed to attach debugger session", ex)
            null
        }
    }

    private fun clearDebugSessionData() {
        debuggerSessionRef.set(null)
        currentConfigRef.set(null)
        evaluationContextRef.set(null)
    }

    @RequiresEdt
    private fun tryAttachToTargetVM(
        debugEnvironment: DebugEnvironment,
        executionEnvironment: ExecutionEnvironment,
        isSilent: Boolean
    ): DebuggerSession? {
        if (project.isDisposed) {
            return null
        }

        val session = executionEnvironment
            .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, isHeadlessMode = isSilent)

        val handler = session.process.processHandler
        if (isSilent) {
            // important
            handler?.startNotify()
        }

        val registeredProcess = DebuggerManagerEx.getInstanceEx(project).getDebugProcess(handler)
        if (registeredProcess == null) {
            LOG.warn("DebugProcess is not registered for the handler right after session start")
        }
        return session
    }

    private fun addProcessListener(debugProcess: DebugProcessImpl?, silent: Boolean) {
        if (debugProcess == null) return
        val listener = NotebookDebugProcessListener(
            project,
            JupyterDebugSessionPath(virtualFile),
            virtualFile,
            silent
        )

        debugProcess.addDebugProcessListener(
            listener, this
        )
    }

    private fun disposeCurrentSession() {
        val session = currentXSession ?: return
        runSafely({
            session.stop()
        }, onFailure = {
            LOG.error("Failed to dispose current session", it)
        }, finally = {
            clearDebugSessionData()
        })
    }

    override fun dispose() {
        // Disposal happens only with parent disposal
        disposeCurrentSession()
    }

}