// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.session

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.PrioritizedTask
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
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.SessionRelatedInfo
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildExecutionEnvironment
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildRemoteRunProfileState
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugConnectionHolder
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugProcessListener
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.NotebookPathProvider
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterSessionPath
import java.util.concurrent.ExecutionException


class KotlinNotebookDebugSession(
    val virtualFile: BackedNotebookVirtualFile,
    private val project: Project,
    projectService: Disposable,
    private val portProvider: () -> Int?
): Disposable {
    init {
      Disposer.register(projectService, this)
    }
    companion object {
        private val LOG = thisLogger()
    }

    val currentSession: XDebugSession?
        get() = debugConnectionHolder.myDebugSession?.xDebugSession

    val debuggerSession: DebuggerSession?
        get() = debugConnectionHolder.myDebugSession

    val debugConnectionHolder = NotebookDebugConnectionHolder(
        virtualFile,
        SessionRelatedInfo(project, virtualFile)
    )

    private var isSilent: Boolean = false
    val isSilentSession: Boolean
        get() = isSilent

    val targetDebugPort: Int? get() = portProvider()

    private val currentProcess: DebugProcessImpl?
        get() = debuggerSession?.process

    private var processListener: NotebookDebugProcessListener? = null

    val isLiveSession: Boolean
        get() = currentSession != null

    private fun updateCurrentSession(xDebugSession: XDebugSession?) {
        if (xDebugSession == null) {
            debugConnectionHolder.clearKnownConnection(project)
        }
    }


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
            val newSession = connectToKernelVirtualMachine(project, debugPort) ?: return@executeOnPooledThread
            LOG.warn("Session was successfully created: $newSession")
        }
    }

    // see JavaAttachDebuggerProvider
    fun connectToKernelVirtualMachine(project: Project, debugPort: Int? = targetDebugPort, transport: Int = 0, forceRestart: Boolean = false, isLocal: Boolean = true, silent: Boolean = true) : DebuggerSession? {
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

        val knownDebugPort = debugConnectionHolder.sessionRelatedInfo.debugPort ?: debugPort
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
        currentSession?.let {
            it.stop()
            debugConnectionHolder.clearKnownConnection(project)
        }
    }

    private fun tryAttachToTargetVM(project: Project, remoteConnection: RemoteConnection, isSilent: Boolean): Boolean {
        var wasSuccessful = true
        val executionEnvironment = debugConnectionHolder.myEnvironment ?: return false
        ApplicationManager.getApplication().invokeAndWait {
            try {
                if (project.isDisposed) {
                    wasSuccessful = false
                    return@invokeAndWait
                }
                val debugEnvironment = DefaultDebugEnvironment(executionEnvironment, debugConnectionHolder.runProfileState, remoteConnection, true)
                debugConnectionHolder.myDebugSession = executionEnvironment
                    .attachDebuggerCreateSession(virtualFile.file.name, project, debugEnvironment, headless = isSilent)

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
        }

        return wasSuccessful
    }


    private fun addProcessListener() {
        val path = debugConnectionHolder.sessionRelatedInfo.sessionPath
            ?: NotebookPathProvider.calculateNotebookPath(project, virtualFile.file, notebookKernelSpec.name)
        processListener = NotebookDebugProcessListener(
            project, JupyterSessionPath(virtualFile), virtualFile, isSilent
        )
        // maybe DebugProcessListener
        debugConnectionHolder.myDebugSession?.process?.addDebugProcessListener(
            processListener
        )
    }


    override fun dispose() {
        debugConnectionHolder.myDebugSession?.process?.removeDebugProcessListener(processListener)
        debugConnectionHolder.clearKnownConnection(project)
        processListener = null
    }

}