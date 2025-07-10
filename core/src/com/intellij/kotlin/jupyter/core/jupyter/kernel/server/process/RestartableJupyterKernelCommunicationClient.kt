// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class RestartableJupyterKernelCommunicationClient(
  val communicationClientFactory: () -> JupyterKernelCommunicationClient,
) : JupyterKernelCommunicationClient {
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var communicationClient: JupyterKernelCommunicationClient = communicationClientFactory()

    @Volatile
    private var closed = false

    override fun send(content: JupyterMessage) {
        if (closed) throw IllegalStateException("Client is closed")
        lock.read {
            if (closed) throw IllegalStateException("Client is closed")
            communicationClient
        }.send(content) // sending should not block close() calls
    }

    fun restart() {
        if (closed) return
        lock.write {
            if (closed) return
            communicationClient.close()
            communicationClient = communicationClientFactory()
        }
    }

    override fun close() {
        if (closed) return
        lock.write {
            if (closed) return
            closed = true
            communicationClient.close()
        }
    }
}
