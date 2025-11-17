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
import com.intellij.kotlin.jupyter.debug.util.DebugSessionConfig
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create

internal object DebugConnectionUtility {
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

    fun buildDebugEnvironment(project: Project, config: DebugSessionConfig): DebugEnvironmentData? {
        val runnerSettings = buildRunnerSettings(
            config.transport,
            config.port.toString(),
            config.isLocal
        )
        val executionEnvironment = project.buildExecutionEnvironment(runnerSettings)
        val remoteConnection = RemoteConnection(true, "127.0.0.1", config.port.toString(), false)
        val runProfileState = executionEnvironment.buildRemoteRunProfileState(remoteConnection)
        val debugEnvironment = DefaultDebugEnvironment(executionEnvironment, runProfileState, remoteConnection, true)

        return DebugEnvironmentData(executionEnvironment, debugEnvironment)
    }


    fun ExecutionEnvironment.buildRemoteRunProfileState(remoteConnection: RemoteConnection): RunProfileState {
        return object : RemoteState {
            override fun getRemoteConnection(): RemoteConnection = remoteConnection
            override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult? {
                return state?.execute(executor, runner)
            }
        }
    }

    fun ExecutionEnvironment.attachDebuggerCreateSession(@Nls sessionName: String, project: Project, debugEnvironment: DebugEnvironment, headless: Boolean = false): DebuggerSession {
        val debugSession = DebuggerManagerEx.getInstanceEx(project).attachVirtualMachine(debugEnvironment)!!
        val starter = object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                return JavaDebugProcess.create(session, debugSession)
            }
        }
        XDebuggerManager.getInstance(project).newSessionBuilder(starter)
            .sessionName(sessionName)
            .environment(this)
            .showTab(!headless)
            .startSession()

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
