// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.util.warnInTests
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import java.nio.file.Path

class KotlinKernelProcessHandler(
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
    class KernelProcessHandler(
        commandLine: GeneralCommandLine,
        val runnableHandler: KotlinKernelProcessHandler
    ) : KillableColoredProcessHandler(commandLine) {
        init {
            setShouldKillProcessSoftly(!ApplicationManager.getApplication().isUnitTestMode)

            addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    LOG.debug(event.text.trimEnd().trimStart('\r', '\n'))
                }

                override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                    if (runnableHandler.stateMachine.terminating()) {
                        runnableHandler.eventDispatcher.multicaster.kernelWillTerminate(KotlinKernelProcessEventImpl(event))
                        LOG.debug("Kernel process is going to be terminated (will ${if (willBeDestroyed) "" else "not "}be destroyed): $event")
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    if (runnableHandler.stateMachine.terminated()) {
                        runnableHandler.eventDispatcher.multicaster.kernelTerminated(KotlinKernelProcessEventImpl(event))
                        LOG.debug("Kernel process terminated with code ${event.exitCode} (${event.text})")
                        LOG.warnInTests { "Destroyed Kotlin kernel ${runnableHandler.kernelId}" }
                    }

                    runnableHandler.eventDispatcher.listeners.clear()
                }
            })
        }

        override fun startNotify() {
            runnableHandler.eventDispatcher.multicaster.beforeNotificationStarted(
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

    val process = KernelProcessHandler(commandLine, this)

    init {
        LOG.warnInTests { "Created Kotlin kernel $kernelId" }
    }

    override fun convertToSpecificListener(listener: KotlinKernelListener): KotlinKernelProcessListener {
        return listener.toProcessListener()
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return KernelZMQClientSession(sessionId, kernelConfig, onMessage)
    }

    override fun stopKernel() {
        process.destroyProcess()
    }

    override fun dispose() {
        stopKernel()
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinKernelProcessHandler::class.java)
    }
}
