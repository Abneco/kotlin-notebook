// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.util.io.BaseOutputReader
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

class KotlinKernelProcessHandler(
    commandLine: GeneralCommandLine,
    private val kernelConfig: KernelConfig,
    val notebookPath: Path,

    private val onKernelTerminated: (ProcessEvent, KotlinKernelProcessHandler) -> Unit,
): KillableColoredProcessHandler(commandLine), KotlinKernelRunnableHandler {

    init {
        val handler = this

        addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                LOG.debug(event.text.trimEnd().trimStart('\r', '\n'))
            }

            override fun processTerminated(event: ProcessEvent) {
                LOG.debug("Kernel process terminated with code ${event.exitCode} (${event.text})")
                onKernelTerminated(event, handler)
            }

            override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
                LOG.debug("Kernel process is going to be terminated (will ${if (willBeDestroyed) "" else "not "}be destroyed): $event")
            }
        })
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

    companion object {
        private val LOG = Logger.getInstance(KotlinKernelProcessHandler::class.java)
    }
}