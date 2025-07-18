// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.AbstractKotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelStartupOptions
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts

class EmbeddedKernelRunnableHandler(
    startupOptions: KernelStartupOptions,
) : AbstractKotlinKernelRunnableHandler<KotlinKernelListener>(
    KotlinKernelListener::class,
    startupOptions,
) {
    val loggerFactory: EmbeddedKotlinKernelLoggerFactory = EmbeddedKotlinKernelLoggerFactory()
    private val kernelConfig: KernelConfig = DefaultKotlinKernelConfigFactory(
        startupOptions,
        ZmqKernelPorts { 0 },
    ).create()

    override fun dispose() {
        val event = EmbeddedKernelEvent(this)
        notifyTerminatedAndDispose(event)
    }

    override fun createSession(sessionId: JupyterNotebookSessionId, onMessage: (JupyterMessage) -> Unit): KotlinKernelSession {
        return EmbeddedKotlinKernelSession(
            project = project,
            kernelConfig = kernelConfig,
            sessionId = sessionId,
            loggerFactory = loggerFactory,
            onMessage = onMessage,
        )
    }
}
