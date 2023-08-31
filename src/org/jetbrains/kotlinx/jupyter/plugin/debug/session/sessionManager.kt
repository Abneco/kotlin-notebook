// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.SessionRelatedInfo
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.attachDebuggerCreateSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildExecutionEnvironment
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.DebugConnectionUtility.buildRemoteRunProfileState
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugConnectionHolder
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.NotebookDebugProcessListener
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.connection.isDebuggerSilentSessionEnabled
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.DebugPortGenerator
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.findNotebookVirtualFileOrNull
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.NotebookPathProvider
import org.jetbrains.plugins.notebooks.jupyter.debugger.JupyterSessionPath
import org.jetbrains.plugins.notebooks.jupyter.variables.common.JupyterVarsToolWindowManager
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

@Service(Service.Level.PROJECT)
class KJupyterDebugSessionManager(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val mapping: MutableMap<BackedNotebookVirtualFile, KJupyterNotebookDebugSession> = ConcurrentHashMap()
    //private val perFileBreakpointManagers: MutableMap<BackedNotebookVirtualFile, KJupyterBreakpointPerFileManager> = ConcurrentHashMap()

    private val portsGenerator = DebugPortGenerator()

    private val nextTargetDebugPortOrNull: Int?
        get() {
            val isKeepOpened = KotlinNotebookProjectOptionsProvider.getInstance(project).shouldOpenDebugPort
            return if (isKeepOpened) portsGenerator.generate() else null
        }

    fun afterScriptingUpdate(virtualFile: BackedNotebookVirtualFile) {
        if (!isDebuggerSilentSessionEnabled) return

        coroutineScope.async {
            withContext(Dispatchers.EDT) {
                JupyterVarsToolWindowManager.getInstance(project).updateVariablesView(virtualFile)
            }
        }
    }

    override fun dispose() {
        mapping.forEach { Disposer.dispose(it.value) }
    }

    fun whichProject(): Project = project

    fun getByPath(path: Path): KJupyterNotebookDebugSession? {
        return mapping.firstNotNullOfOrNull {
            if (it.key.file.path == path.toString()) it.value else null
        } ?: run {
            val backedNotebookVirtualFile = path.findNotebookVirtualFileOrNull() ?: return null
            get(backedNotebookVirtualFile)
        }
    }

    fun get(virtualFile: BackedNotebookVirtualFile): KJupyterNotebookDebugSession {
        return mapping.getOrPut(virtualFile) {
            KJupyterNotebookDebugSession(
                virtualFile,
                this
            ) { nextTargetDebugPortOrNull }
        }
    }

    /*fun getBreakpointManager(virtualFile: BackedNotebookVirtualFile): KJupyterBreakpointPerFileManager {
        return perFileBreakpointManagers.getOrPut(virtualFile) { KJupyterBreakpointPerFileManager(virtualFile,  project, this) }
    }*/

    companion object {
        fun getInstance(project: Project) = project.service<KJupyterDebugSessionManager>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): KJupyterNotebookDebugSession {
            return getInstance(project).get(virtualFile)
        }

        /*fun getBreakpointManagerForFile(project: Project, virtualFile: BackedNotebookVirtualFile): KJupyterBreakpointPerFileManager {
            return getInstance(project).getBreakpointManager(virtualFile)
        }*/
    }
}

class KJupyterNotebookDebugSession(
    val virtualFile: BackedNotebookVirtualFile,
    private val projectService: KJupyterDebugSessionManager,
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
        SessionRelatedInfo(projectService.whichProject(), virtualFile)
    )

    private var isSilent: Boolean = false
    val isSilentSession: Boolean
        get() = isSilent

    val targetDebugPort: Int? get() = portProvider()

    //val breakpointPerFileManager = projectService.getBreakpointManager(virtualFile)

    private val currentProcess: DebugProcessImpl?
        get() = debuggerSession?.process

    private var processListener: NotebookDebugProcessListener? = null

    val isLiveSession: Boolean
        get() = currentSession != null

    private fun updateCurrentSession(xDebugSession: XDebugSession?) {
        if (xDebugSession == null) {
            debugConnectionHolder.clearKnownConnection(projectService.whichProject())
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

    fun ensureSilentSessionAlive(project: Project, debugPort: Int? = targetDebugPort) {
        if (isLiveSession || debugPort == null || !isDebuggerSilentSessionEnabled) return

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

        if (debugPort == null) return null
        if (!isDebuggerSilentSessionEnabled) {
            LOG.warn("Silent debugger session is disabled, check registry key: kotlin.notebook.silent.debug.session.enabled")
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
            debugConnectionHolder.clearKnownConnection(projectService.whichProject())
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
        val project = projectService.whichProject()
        val path = debugConnectionHolder.sessionRelatedInfo.sessionPath
            ?: NotebookPathProvider.calculateNotebookPath(project, virtualFile.file, notebookKernelSpec.name)
        processListener = NotebookDebugProcessListener(
            projectService.whichProject(), JupyterSessionPath(virtualFile), virtualFile, isSilent
        )
        // maybe DebugProcessListener
        debugConnectionHolder.myDebugSession?.process?.addDebugProcessListener(
            processListener
        )
    }


    override fun dispose() {
        debugConnectionHolder.myDebugSession?.process?.removeDebugProcessListener(processListener)
        debugConnectionHolder.clearKnownConnection(projectService.whichProject())
        processListener = null
    }

}