// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.EventDispatcher
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelState
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.util.findNotebookVirtualFileOrNull
import org.jetbrains.kotlinx.jupyter.plugin.util.warnInTests
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class KotlinKernelProcessHandler(
    override val project: Project,
    override val kernelId: JupyterKernelId,
    commandLine: GeneralCommandLine,
    private val kernelConfig: KernelConfig,
    val notebookPath: Path,
): KillableColoredProcessHandler(commandLine), KotlinKernelRunnableHandler {

    private val _kernelState = AtomicReference(KernelState.STARTING)
    override val kernelState: KernelState = _kernelState.get()
    
    override fun markStarted() {
        _kernelState.compareAndSet(KernelState.STARTING, KernelState.STARTED)
    }

    private val eventDispatcher = EventDispatcher.create(KotlinKernelProcessListener::class.java)

    override val notebookVirtualFile by lazy {
        notebookPath.findNotebookVirtualFileOrNull()
    }

    init {
        LOG.warnInTests { "Created Kotlin kernel $kernelId" }

        setShouldKillProcessSoftly(!ApplicationManager.getApplication().isUnitTestMode)

        addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                LOG.debug(event.text.trimEnd().trimStart('\r', '\n'))
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.debug("Kernel process terminated with code ${event.exitCode} (${event.text})")
                LOG.warnInTests { "Destroyed Kotlin kernel $kernelId" }
                eventDispatcher.multicaster.kernelTerminated(KotlinKernelProcessEventImpl(event))
                eventDispatcher.listeners.clear()
            }

            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                LOG.debug("Kernel process is going to be terminated (will ${if (willBeDestroyed) "" else "not "}be destroyed): $event")
            }
        })
    }

    override fun addKernelListener(listener: KotlinKernelListener) {
        addKernelProcessListener(listener.toProcessListener())
    }

    fun addKernelProcessListener(listener: KotlinKernelProcessListener) {
        eventDispatcher.addListener(listener)
    }

    override fun startNotify() {
        eventDispatcher.multicaster.beforeNotificationStarted(KotlinKernelNotificationStartedEvent(this))
        super.startNotify()
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return KernelZMQClientSession(sessionId, kernelConfig, onMessage)
    }

    override fun canStopKernel(): Boolean {
        return !isProcessTerminated && !isProcessTerminating
    }

    override fun stopKernel() {
        destroyProcess()
    }

    override fun dispose() {
        destroyProcess()
    }

    override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.forMostlySilentProcess()
    }

    override fun isSilentlyDestroyOnClose(): Boolean {
        return true
    }

    companion object {
        private val LOG = Logger.getInstance(KotlinKernelProcessHandler::class.java)
    }
}
