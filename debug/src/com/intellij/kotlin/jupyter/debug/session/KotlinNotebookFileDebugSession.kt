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
import java.util.concurrent.ExecutionException
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

    @Volatile
    private var currentConfig: DebugSessionConfig? = null

    private val eventsHandler = NotebookDebugEventsHandler(project, virtualFile)

    init {
        project.initServiceListeners(this)

        val port = portProvider()
        if (port != null) {
            currentConfig = DebugSessionConfig(port)
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
                    if (debuggerSession?.isConnecting == true || updateState.isIncomplete) return

                    messageBus.syncPublisher(JupyterEnvironmentUpdateListener.TOPIC)
                        .onRuntimeEnvironmentUpdate(virtualFile, null)
                }
            }
        )

        JupyterExecutionListener.register(parentDisposable, object : JupyterExecutionListener {
            override suspend fun sessionCreated(session: JupyterNotebookSession) {
                if (project.isDisposed) return
                if (session.virtualFile != virtualFile) return

                val port = targetDebugPort ?: return
                val session = getOrCreateDebuggerSession(
                    DebugSessionConfig(port),
                    forceRestart = true
                )
                LOG.info("Debugger session after start: $session")
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
            evaluationContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
        }
        eventsHandler.handleInternalDebugMethodEntryEvent(suspendContext, event)
    }

    private val myDebugSession: AtomicReference<DebuggerSession?> = AtomicReference(null)
    private val sessionMutex = Mutex()

    val currentStackFrameProxy: StackFrameProxyImpl?
        get() = debuggerSession?.process?.debuggerContext?.frameProxy

    @Volatile
    var evaluationContext: EvaluationContextImpl? = null

    val targetDebugPort: Int? get() = currentConfig?.port

    fun provideFreshDebugPort(): Int? {
        val port = portProvider() ?: return null
        currentConfig = DebugSessionConfig(port)
        return port
    }

    fun prepareInternalRequests(debugProcess: DebugProcessImpl) {
        if (!debugFeaturesEnabled) return

        kernelThreadBreakpoint.createRequest(debugProcess)
    }

    val currentXSession: XDebugSession?
        get() = myDebugSession.get()?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = myDebugSession.get()

    private var processListener: NotebookDebugProcessListener? = null

    val isLiveSession: Boolean
        get() = currentXSession != null

    private fun DebuggerSession.configureSessionAfterAttach(config: DebugSessionConfig) {
        addProcessListener(process)

        if (!config.silent) {
            XDebuggerManagerImpl.getNotificationGroup().createNotification(
                KotlinNotebookDebugBundle.message("kotlin.jupyter.debug.support.text"), MessageType.INFO
            ).notify(project)
        }
    }

    // see JavaAttachDebuggerProvider
    internal suspend fun getOrCreateDebuggerSession(
        config: DebugSessionConfig,
        forceRestart: Boolean = false
    ): DebuggerSession? {
        if (checkSessionCouldBeReused(forceRestart)) {
            return debuggerSession
        }

        sessionMutex.withLock {
            if (checkSessionCouldBeReused(forceRestart)) {
                return debuggerSession
            }
            disposeCurrentSession()

            DebuggerSettings.getInstance().transport = config.transport
            currentConfig = config

            val environmentData = DebugConnectionUtility.buildDebugEnvironment(project, config) ?: return null

            val newSession = withContext(Dispatchers.EDT) {
                tryAttachToTargetVM(
                    environmentData.debugEnvironment,
                    environmentData.executionEnvironment,
                    config.silent
                )
            }
            if (newSession == null) {
                clearDebugSessionData()
                return null
            }
            myDebugSession.set(newSession)

            newSession.configureSessionAfterAttach(config)

            return newSession
        }
    }

    private fun checkSessionCouldBeReused(forceRestart: Boolean): Boolean {
        if (!isLiveSession) return false

        return !forceRestart && debuggerSession?.isAttached == true
    }

    private fun clearDebugSessionData() {
        myDebugSession.set(null)
        currentConfig = null
    }

    @RequiresEdt
    private fun tryAttachToTargetVM(
        debugEnvironment: DebugEnvironment,
        executionEnvironment: ExecutionEnvironment,
        isSilent: Boolean
    ): DebuggerSession? {
        return try {
            if (project.isDisposed) {
                return null
            }

            val session = executionEnvironment
                .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, headless = isSilent)

            if (project.isDisposed) {
                return null
            }
            val handler = session.process.processHandler
            if (isSilent) {
                // important
                handler?.startNotify()
            }

            DebuggerManagerEx.getInstanceEx(project).getDebugProcess(
                handler
            )!!

            session
        } catch (ex: ExecutionException) {
            LOG.error("Unsuccessful attach to targetVM: $ex")
            null
        }
    }

    private fun addProcessListener(debugProcess: DebugProcessImpl?) {
        processListener = NotebookDebugProcessListener(
            project, JupyterDebugSessionPath(virtualFile),
            virtualFile,
            currentConfig?.silent ?: true
        )

        debugProcess?.addDebugProcessListener(
            processListener, this
        )
    }

    fun disposeCurrentSession() {
        currentXSession?.let {
            it.stop()
            clearDebugSessionData()
        }
    }

    override fun dispose() {
        disposeCurrentSession()
        clearDebugSessionData()
        processListener = null
    }

}