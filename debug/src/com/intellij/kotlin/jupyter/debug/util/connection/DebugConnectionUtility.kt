// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util.connection

import com.intellij.debugger.DebugEnvironment
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RemoteState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create

internal object DebugConnectionUtility {
    /**
     * Marker key needed to distinguish a process during the session initialization phase
     */
    internal val NOTEBOOK_PROCESS_MARKER_KEY = Key.create<Boolean>("NotebookProcessKeyMarker")

    fun Project.buildExecutionEnvironment(runnerSettings: RunnerSettings): ExecutionEnvironment =
        ExecutionEnvironmentBuilder(
            this, DefaultDebugExecutor.getDebugExecutorInstance()
        )
        .runnerSettings(runnerSettings)
        .runProfile(
            RemoteConfiguration(this, RemoteConfigurationType.getInstance())
        ).build()


    fun buildRunnerSettings(transport: Int, debugPort: String, local: Boolean = true): GenericDebuggerRunnerSettings {
        return GenericDebuggerRunnerSettings().apply {
            this.transport = transport
            setLocal(local)
            this.debugPort = debugPort
        }
    }

    fun buildDebugEnvironment(
        project: Project,
        config: DebugSessionConfig,
        notebookFile: BackedNotebookVirtualFile? = null
    ): DebugEnvironmentData? {
        val runnerSettings = buildRunnerSettings(
            config.transport,
            config.port.toString(),
            config.isLocal
        )
        val executionEnvironment = project.buildExecutionEnvironment(runnerSettings)
        val remoteConnection = RemoteConnection(true, "127.0.0.1", config.port.toString(), false)
        val runProfileState = executionEnvironment.buildRemoteRunProfileState(remoteConnection)

        val debugEnvironment = if (notebookFile != null) {
            NotebookDebugEnvironment(
                executionEnvironment,
                runProfileState,
                remoteConnection,
                pollTimeout = LOCAL_START_TIMEOUT,
                notebookFile,
                project
            )
        }
        else {
            DefaultDebugEnvironment(executionEnvironment, runProfileState, remoteConnection, true)
        }

        return DebugEnvironmentData(executionEnvironment, debugEnvironment)
    }

    private const val LOCAL_START_TIMEOUT = 0L


    fun ExecutionEnvironment.buildRemoteRunProfileState(remoteConnection: RemoteConnection): RunProfileState {
        return object : RemoteState {
            override fun getRemoteConnection(): RemoteConnection = remoteConnection
            override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
                return state?.execute(executor, runner)
            }
        }
    }

    /**
     * Creates a debugger session by connecting to a target VM.
     * [isHeadlessMode] determines if it should be run without any UI.
     * In that case, breakpoints should be muted not to stop on them visually.
     */
    fun ExecutionEnvironment.attachDebuggerCreateSession(
        @Nls sessionName: String,
        project: Project,
        debugEnvironment: DebugEnvironment,
        isHeadlessMode: Boolean = false
    ): DebuggerSession {
        val debugSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)!!
        debugSession.process.putUserData(NOTEBOOK_PROCESS_MARKER_KEY, true)
        val starter = object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                return JavaDebugProcess.create(session, debugSession)
            }
        }
        val xDebugSession = XDebuggerManager.getInstance(project).newSessionBuilder(starter)
            .sessionName(sessionName)
            .environment(this)
            .showTab(!isHeadlessMode)
            .startSession().session

        if (isHeadlessMode) {
            xDebugSession.setBreakpointMuted(true)
        }

        //debugSession.isModifiedClassesScanRequired = true // for hot-swap
        return debugSession
    }

    // Random number
    const val minimumDebugPort: Int = 5000

    // 16-bit maximum value
    const val maximumDebugPort: Int = 1 shl 16

    val debugPortsGenerator: PortsGenerator
        get() = PortsGenerator.create(minimumDebugPort, maximumDebugPort)

}
