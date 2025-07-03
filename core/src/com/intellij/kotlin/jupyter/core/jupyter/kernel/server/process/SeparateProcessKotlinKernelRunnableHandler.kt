// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelStateMachine
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.DONT_ACCEPT_SHUTDOWN
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.warnInTests
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EventDispatcher
import com.intellij.util.application
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.nio.file.Path

/**
 * A handler for managing a separate process that runs a Kotlin Jupyter kernel.
 *
 * This class is responsible for handling the lifecycle and communication with a separate OS-level
 * process that executes a Kotlin Jupyter kernel.
 * It initiates the process, manages its state, and ensures proper termination when required.
 * This class is also responsible for all Jupyter-related
 * things, and most importantly for [createSession].
 *
 * @param project The current IDEA project.
 * @param kernelId Generated identifier for the Jupyter kernel.
 * @param commandLine The command line used to start the kernel process.
 * @param kernelConfig Configuration settings for the kernel.
 * @param notebookPath The file path of the Jupyter notebook.
 * @param notebookVirtualFile The virtual file of the Jupyter notebook, if any.
 */
class SeparateProcessKotlinKernelRunnableHandler(
    project: Project,
    kernelId: JupyterKernelId,
    commandLine: GeneralCommandLine,
    private val kernelConfig: KernelConfig,
    notebookPath: Path,
    notebookVirtualFile: BackedNotebookVirtualFile?,
): AbstractKotlinKernelRunnableHandler<KotlinKernelProcessListener>(
    KotlinKernelProcessListener::class,
    project, kernelId, notebookPath, notebookVirtualFile
) {
    val process: KillableProcessHandler = KernelOsProcessHandler(commandLine, this)

    init {
        LOG.warnInTests { "Created Kotlin kernel $kernelId" }
    }

    override fun convertBaseListener(listener: KotlinKernelListener): KotlinKernelProcessListener {
        return listener.toProcessListener()
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return KernelZmqClientSession(sessionId, kernelConfig, onMessage, DONT_ACCEPT_SHUTDOWN)
    }

    override fun dispose() {
        LOG.debug("Stopping kernel process", Throwable())
        process.destroyProcess()
    }

    /**
     * A handler for managing OS-level processes that run Kotlin Jupyter kernels.
     *
     * @param commandLine The command line used to start the process.
     * @param runnableHandler The handler responsible for managing the kernel's state.
     *
     * This class extends KillableColoredProcessHandler, providing functionalities to create,
     * handle, and terminate a process.
     * It listens to process events and manages process termination by interacting with [runnableHandler].
     */
    class KernelOsProcessHandler(
        commandLine: GeneralCommandLine,
        val runnableHandler: SeparateProcessKotlinKernelRunnableHandler
    ) : KillableColoredProcessHandler(commandLine) {
        val stateMachine: KernelStateMachine
            get() = runnableHandler.stateMachine
        val eventDispatcher: EventDispatcher<KotlinKernelProcessListener>
            get() = runnableHandler.eventDispatcher

        init {
            // Kotlin kernel process can't be killed gracefully on Linux.
            // For now, reasons are unknown, should be investigated.
            // On Windows, a process should not be killed plainly, e.g., without using specific Windows API.
            setShouldKillProcessSoftly(!application.isUnitTestMode && !SystemInfo.isLinux || SystemInfo.isWindows)

            addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    LOG.debug(event.text.trimEnd().trimStart('\r', '\n'))
                }

                override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                    if (stateMachine.terminating()) {
                        eventDispatcher.multicaster.kernelWillTerminate(KotlinKernelProcessEventImpl(event))
                        LOG.info("Kernel process is going to be terminated (will ${if (willBeDestroyed) "" else "not "}be destroyed): $event")
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    if (stateMachine.terminated()) {
                        eventDispatcher.multicaster.kernelTerminated(KotlinKernelProcessEventImpl(event))
                        LOG.info("Kernel process terminated with code ${event.exitCode} (${event.text})")
                        LOG.warnInTests { "Destroyed Kotlin kernel ${runnableHandler.kernelId}" }
                    }

                    eventDispatcher.listeners.clear()
                }
            })
        }

        override fun startNotify() {
            eventDispatcher.multicaster.beforeNotificationStarted(
                KotlinKernelNotificationStartedEvent(runnableHandler)
            )
            super.startNotify()
        }

        override fun readerOptions(): BaseOutputReader.Options {
            return BaseOutputReader.Options.forMostlySilentProcess()
        }

        override fun isSilentlyDestroyOnClose(): Boolean {
            return true
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}
