// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.session

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.plugin.debug.breakpoint.KernelSyntheticMethodBreakpoint
import org.jetbrains.kotlinx.jupyter.plugin.debug.events.NotebookDebugEventsHandler
import org.jetbrains.kotlinx.jupyter.plugin.debug.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.SessionRelatedInfo
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildExecutionEnvironment
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildRemoteRunProfileState
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugConnectionHolder
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugProcessListener
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterSessionPath
import org.jetbrains.plugins.notebooks.jupyter.editor.completion.JupyterRuntimeProcessListener
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import java.util.concurrent.ExecutionException


class KotlinNotebookDebugSession(
    val virtualFile: BackedNotebookVirtualFile,
    private val project: Project,
    projectService: Disposable,
    private val coroutineScope: CoroutineScope,
    private val portProvider: () -> Int?
): Disposable {
    val currentStackFrameProxy: StackFrameProxyImpl?
        get() = debuggerSession?.process?.debuggerContext?.frameProxy

    @Volatile
    var evaluationContext: EvaluationContextImpl? = null
    private val eventsHandler = NotebookDebugEventsHandler(project, virtualFile)

    private val kernelThreadBreakpoint = KernelSyntheticMethodBreakpoint(
        project,
        KotlinNotebookSessionInternalNamesProvider.notebookClassName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugMethodName,
        KotlinNotebookSessionInternalNamesProvider.notebookDebugMethodBreakpointLineNumber,
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
        kernelThreadBreakpoint.createRequest(debugProcess)
    }

    private val debugConnectionHolder = NotebookDebugConnectionHolder(
        virtualFile,
        SessionRelatedInfo(project, virtualFile)
    )

    private fun Project.initServiceListeners(parentDisposable: Disposable) {
        val messageBus = messageBus
        messageBus.connect(parentDisposable).subscribe(
            NotebookCodeSnippetsChangeListener.TOPIC,
            object : NotebookCodeSnippetsChangeListener {
                override fun scriptsClassesChanged(file: BackedNotebookVirtualFile) {
                    if (debuggerSession?.isConnecting == true) return

                    messageBus.syncPublisher(JupyterRuntimeProcessListener.TOPIC)
                        .notebookSessionEnvironmentUpdated(virtualFile.file, null)
                }
            }
        )

        messageBus.connect(parentDisposable).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun kernelStarted(virtualFile: BackedNotebookVirtualFile) {
                    this@KotlinNotebookDebugSession.coroutineScope.launch {
                        if (project.isDisposed) return@launch

                        val session = getOrCreateDebuggerSession(project, forceRestart = true)
                        LOG.warn("Session after start: $session")
                    }
                }
            }
        )
    }

    init {
        Disposer.register(projectService, this)
        project.initServiceListeners(projectService)

        debugConnectionHolder.sessionRelatedInfo.updateWith(project, targetDebugPort)
    }

    companion object {
        private val LOG = thisLogger()
    }

    val currentXSession: XDebugSession?
        get() = debugConnectionHolder.myDebugSession?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = debugConnectionHolder.myDebugSession

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

        ApplicationManager.getApplication().executeOnPooledThread {
            val newSession = getOrCreateDebuggerSession(project, debugPort) ?: return@executeOnPooledThread
            LOG.warn("Session was successfully created: $newSession")
        }
    }

    // see JavaAttachDebuggerProvider
    @Synchronized
    fun getOrCreateDebuggerSession(project: Project, debugPort: Int? = targetDebugPort, transport: Int = 0, forceRestart: Boolean = false, isLocal: Boolean = true, silent: Boolean = true) : DebuggerSession? {
        if (isLiveSession) {
            if (forceRestart) {
                disposeCurrentSession()
            } else return debuggerSession
        }

        if (debugPort == null) {
            LOG.debug("Could not connect to a debugger session, debug port provided is null")
            return null
        }

        DebuggerSettings.getInstance().transport = transport
        debugConnectionHolder.sessionRelatedInfo.updateWith(project, debugPort)

        val knownDebugPort = debugConnectionHolder.sessionRelatedInfo.debugPort ?: return null

        val runnerSettings = DebugConnectionUtility.buildRunnerSettings(transport, knownDebugPort.toString(), isLocal)
        val executionEnvironment = project.buildExecutionEnvironment(runnerSettings)
        val remoteConnection = RemoteConnection(true, "127.0.0.1", knownDebugPort.toString(), false)

        debugConnectionHolder.myEnvironment = executionEnvironment
        debugConnectionHolder.runProfileState = executionEnvironment.buildRemoteRunProfileState(remoteConnection)
        val wasSuccessful = tryAttachToTargetVM(project, remoteConnection, silent)
        if (!wasSuccessful) {
            debugConnectionHolder.clearKnownConnection(project)
            return null
        }
        isSilent = silent

        addProcessListener()
        if (!silent) {
            XDebuggerManagerImpl.getNotificationGroup().createNotification(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.support.text"), MessageType.INFO
            ).notify(project)
        }

        return debuggerSession
    }

    fun disposeCurrentSession() {
        currentXSession?.let {
            it.stop()
            debugConnectionHolder.clearKnownConnection(project)
        }
    }

    fun updateSessionCellInfo(
        cell: JupyterPsiCell,
        cellPointer: NotebookIntervalPointer,
        cellFileName: String? = null,
        sessionPath: String? = null
    ) {
        debugConnectionHolder.sessionRelatedInfo.apply {
            this.cell = cell
            this.cellPointer = cellPointer
            this.cellFileName = cellFileName
            this.sessionPath = sessionPath
        }
    }

    private fun tryAttachToTargetVM(project: Project, remoteConnection: RemoteConnection, isSilent: Boolean): Boolean {
        var wasSuccessful = true
        val executionEnvironment = debugConnectionHolder.myEnvironment ?: return false
        try {
            if (project.isDisposed) {
                return false
            }
            val debugEnvironment = DefaultDebugEnvironment(executionEnvironment, debugConnectionHolder.runProfileState, remoteConnection, true)
            ApplicationManager.getApplication().invokeAndWait {
                debugConnectionHolder.myDebugSession = executionEnvironment
                    .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, headless = isSilent)
            }

            val handler = debugConnectionHolder.myDebugSession?.process?.processHandler
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

        debugConnectionHolder.myDebugSession?.process?.addDebugProcessListener(
            processListener
        )
    }


    override fun dispose() {
        debugConnectionHolder.myDebugSession?.process?.removeDebugProcessListener(processListener)
        disposeCurrentSession()
        debugConnectionHolder.clearKnownConnection(project)
        processListener = null
    }

}