// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.execution.ui.ConsoleView
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.jupyter.execution.kernel.AbstractKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import com.intellij.jupyter.execution.listeners.KernelListener
import com.intellij.jupyter.execution.kernel.JupyterKernelConnection
import com.intellij.jupyter.execution.kernel.KernelProcessAttachable
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts

class EmbeddedKernelRunnableHandler(
    startupOptions: KernelStartupOptions,
) : AbstractKernelRunnableHandler<KernelListener>(
    KernelListener::class,
    startupOptions,
), KernelProcessAttachable {
    val loggerFactory: EmbeddedKotlinKernelLoggerFactory = EmbeddedKotlinKernelLoggerFactory()
    private val kernelConfig: KernelConfig<KotlinKernelOwnParams> = DefaultKotlinKernelConfigFactory(
        startupOptions,
        ZmqKernelPorts { 0 },
    ).create()

    override fun dispose() {
        val event = EmbeddedKernelEvent(this)
        notifyTerminatedAndDispose(event)
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): JupyterKernelConnection {
        return EmbeddedKotlinKernelSession(
            project = project,
            kernelConfig = kernelConfig,
            sessionId = sessionId,
            loggerFactory = loggerFactory,
            onMessage = onMessage,
        )
    }

    override fun attachToProcess(console: ConsoleView) {
        loggerFactory.consoleView = console
    }
}
