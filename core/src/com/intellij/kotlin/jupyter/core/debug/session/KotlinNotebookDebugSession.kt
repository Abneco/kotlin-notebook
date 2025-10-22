// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.session

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.debugger.common.JupyterSessionPath
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.kotlin.jupyter.core.debug.breakpoint.KernelSyntheticMethodBreakpoint
import com.intellij.kotlin.jupyter.core.debug.events.NotebookDebugEventsHandler
import com.intellij.kotlin.jupyter.core.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.intellij.kotlin.jupyter.core.debug.util.DebugSessionConfig
import com.intellij.kotlin.jupyter.core.debug.util.SessionRelatedInfo
import com.intellij.kotlin.jupyter.core.debug.util.connection.DebugConnectionUtility
import com.intellij.kotlin.jupyter.core.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import com.intellij.kotlin.jupyter.core.debug.util.connection.DebugConnectionUtility.buildExecutionEnvironment
import com.intellij.kotlin.jupyter.core.debug.util.connection.DebugConnectionUtility.buildRemoteRunProfileState
import com.intellij.kotlin.jupyter.core.debug.util.connection.NotebookDebugProcessListener
import com.intellij.kotlin.jupyter.core.debug.util.debugFeaturesEnabled
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.Companion.isIncomplete
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import java.util.concurrent.ExecutionException

internal class KotlinNotebookDebugSession(
    public override val virtualFile: BackedNotebookVirtualFile,
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val portProvider: () -> Int?
): NotebookPerFileChildService(virtualFile, coroutineScope) {
    val currentStackFrameProxy: StackFrameProxyImpl?
        get() = debuggerSession?.process?.debuggerContext?.frameProxy

    @Volatile
    var evaluationContext: EvaluationContextImpl? = null
    private val eventsHandler = NotebookDebugEventsHandler(project, virtualFile)

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

    @Volatile
    private var _debugPort: Int? = portProvider()
    val targetDebugPort: Int? get() = _debugPort

    fun provideFreshDebugPort(): Int? {
        _debugPort = portProvider()
        return targetDebugPort
    }

    fun prepareInternalRequests(debugProcess: DebugProcessImpl) {
        if (!debugFeaturesEnabled) return

        kernelThreadBreakpoint.createRequest(debugProcess)
    }

    private val sessionInfo = SessionRelatedInfo(project, virtualFile)

    @Volatile
    private var myDebugSession: DebuggerSession? = null

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

                val port = targetDebugPort ?: return
                val session = getOrCreateDebuggerSession(project,
                    DebugSessionConfig(port),
                    forceRestart = true)
                LOG.info("Debugger session after start: $session")
            }
        })
    }

    init {
        project.initServiceListeners(this)

        sessionInfo.updateWith(project, targetDebugPort)
    }

    companion object {
        private val LOG = notebookLogger()
    }

    val currentXSession: XDebugSession?
        get() = myDebugSession?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = myDebugSession

    private var isSilent: Boolean = false

    private var processListener: NotebookDebugProcessListener? = null

    val isLiveSession: Boolean
        get() = currentXSession != null

    fun trySuspend() {
        debuggerSession?.process?.managerThread?.invoke(PrioritizedTask.Priority.HIGH) {
            debuggerSession?.pause()
        }
    }

    fun onCellExecutedCallback() {
        if (isSilent) return
        if (isLiveSession) {
            disposeCurrentSession()
        }
    }

    fun ensureSilentSessionAlive(debugPort: Int? = targetDebugPort) {
        if (isLiveSession || debugPort == null) return

        coroutineScope.async {
            val newSession = getOrCreateDebuggerSession(
                project,
                DebugSessionConfig(debugPort)
            ) ?: return@async
            LOG.warn("Session was successfully created: $newSession")
        }
    }

    // see JavaAttachDebuggerProvider
    @Synchronized
    fun getOrCreateDebuggerSession(
        project: Project,
        config: DebugSessionConfig,
        forceRestart: Boolean = false
    ): DebuggerSession? {
        if (isLiveSession) {
            if (forceRestart) {
                disposeCurrentSession()
            } else return debuggerSession
        }

        DebuggerSettings.getInstance().transport = config.transport
        sessionInfo.updateWith(project, config.port)

        val knownDebugPort = sessionInfo.debugPort ?: return null

        val runnerSettings = DebugConnectionUtility.buildRunnerSettings(config.transport, knownDebugPort.toString(), config.isLocal)
        val executionEnvironment = project.buildExecutionEnvironment(runnerSettings)
        val remoteConnection = RemoteConnection(true, "127.0.0.1", knownDebugPort.toString(), false)
        val runProfileState = executionEnvironment.buildRemoteRunProfileState(remoteConnection)

        val wasSuccessful = tryAttachToTargetVM(project, executionEnvironment, runProfileState, remoteConnection, config.silent)
        if (!wasSuccessful) {
            clearDebugSession()
            return null
        }
        isSilent = config.silent

        coroutineScope.async {
            addProcessListener()

            if (!config.silent) {
                XDebuggerManagerImpl.getNotificationGroup().createNotification(
                    KotlinNotebookBundle.message("kotlin.jupyter.debug.support.text"), MessageType.INFO
                ).notify(project)
            }
        }

        return debuggerSession
    }

    private fun clearDebugSession() {
        myDebugSession = null
        sessionInfo.debugPort = null
        sessionInfo.updateWith(project)
    }

    fun disposeCurrentSession() {
        currentXSession?.let {
            it.stop()
            clearDebugSession()
        }
    }

    fun updateSessionCellInfo(
      cell: JupyterPsiCell,
      cellPointer: NotebookIntervalPointer,
      cellFileName: String? = null,
      sessionPath: String? = null
    ) {
        sessionInfo.apply {
            this.cell = cell
            this.cellPointer = cellPointer
            this.cellFileName = cellFileName
            this.sessionPath = sessionPath
        }
    }

    private fun tryAttachToTargetVM(
        project: Project,
        executionEnvironment: ExecutionEnvironment,
        runProfileState: RunProfileState,
        remoteConnection: RemoteConnection,
        isSilent: Boolean
    ): Boolean {
        var wasSuccessful = true
        try {
            if (project.isDisposed) {
                return false
            }

            val debugEnvironment = DefaultDebugEnvironment(executionEnvironment, runProfileState, remoteConnection, true)
            ApplicationManager.getApplication().invokeAndWait {
                if (project.isDisposed) return@invokeAndWait
                myDebugSession = executionEnvironment
                    .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, headless = isSilent)
            }

            if (project.isDisposed) {
                return false
            }
            val handler = myDebugSession?.process?.processHandler
            if (isSilent) {
                // important
                handler?.startNotify()
            }

            DebuggerManagerEx.getInstanceEx(project).getDebugProcess(
                handler
            )!!
        } catch (ex: ExecutionException) {
            LOG.error("Unsuccessful attach to targetVM: $ex")
            wasSuccessful = false
        }

        return wasSuccessful
    }


    private fun addProcessListener() {
        processListener = NotebookDebugProcessListener(
            project, JupyterSessionPath(virtualFile), virtualFile, isSilent
        )

        myDebugSession?.process?.addDebugProcessListener(
            processListener, this
        )
    }


    override fun dispose() {
        disposeCurrentSession()
        clearDebugSession()
        processListener = null
    }

}