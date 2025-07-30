// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.JupyterMessageFilter
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.ws.JupyterWsClientSocketManager
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqClientSocketManager
import org.jetbrains.kotlinx.jupyter.zmq.protocol.JupyterZmqClientSockets

sealed class KernelClientSession(
    override val sessionId: JupyterNotebookSessionId,
    jupyterParams: KernelJupyterParams,
    onMessageCallback: (JupyterMessage) -> Unit,
    private val outgoingMessagesFilter: JupyterMessageFilter,
    communicationClientFactory: (
        sessionId: JupyterNotebookSessionId,
        jupyterParams: KernelJupyterParams,
        onMessageCallback: (JupyterMessage) -> Unit,
    ) -> JupyterKernelCommunicationClient,
) : JupyterKernelCommunicationClient, KotlinKernelSession {

    private val communicationClient = RestartableJupyterKernelCommunicationClient {
        communicationClientFactory(sessionId, jupyterParams, onMessageCallback)
    }

    fun restartCommunication(): Unit = communicationClient.restart()

    override fun send(content: JupyterMessage) {
        if (!outgoingMessagesFilter.accepts(content)) return
        communicationClient.send(content)
    }
    override fun close(): Unit = communicationClient.close()

    override fun dispose(): Unit = close()
}

class KernelZmqClientSession(
    sessionId: JupyterNotebookSessionId,
    jupyterParams: KernelJupyterParams,
    onMessageCallback: (JupyterMessage) -> Unit,
    outgoingMessagesFilter: JupyterMessageFilter,
) : KernelClientSession(
    sessionId = sessionId,
    jupyterParams = jupyterParams,
    onMessageCallback = onMessageCallback,
    outgoingMessagesFilter = outgoingMessagesFilter,
    communicationClientFactory = ::ZmqJupyterKernelCommunicationClient,
)

private class ZmqJupyterKernelCommunicationClient(
    sessionId: JupyterNotebookSessionId,
    jupyterParams: KernelJupyterParams,
    onMessageCallback: (JupyterMessage) -> Unit
) : KotlinJupyterKernelCommunicationClient<JupyterZmqClientSockets>(
    sessionId = sessionId,
    onMessageCallback = onMessageCallback,
    sockets = JupyterZmqClientSocketManager(DefaultKernelLoggerFactory, JupyterSocketSide.IDE_CLIENT)
        .open(jupyterParams)
) {
    override fun closeSockets() {
        super.closeSockets()
        for (zmqPoller in getPollersFromContext(sockets.context)) {
            if (zmqPoller.workerThread?.isAlive != false) {
                notebookLogger().warn("Undisposed ZMQ Poller $zmqPoller detected, interrupting polling")
                zmqPoller.closeSafely()
            }
        }
    }
}

class KernelWsClientSession(
    sessionId: JupyterNotebookSessionId,
    jupyterParams: KernelJupyterParams,
    onMessageCallback: (JupyterMessage) -> Unit,
    outgoingMessagesFilter: JupyterMessageFilter,
) : KernelClientSession(
    sessionId = sessionId,
    jupyterParams = jupyterParams,
    onMessageCallback = onMessageCallback,
    outgoingMessagesFilter = outgoingMessagesFilter,
    communicationClientFactory = ::WsJupyterKernelCommunicationClient,
)

private class WsJupyterKernelCommunicationClient(
    sessionId: JupyterNotebookSessionId,
    jupyterParams: KernelJupyterParams,
    onMessageCallback: (JupyterMessage) -> Unit,
) : KotlinJupyterKernelCommunicationClient<JupyterClientSockets>(
    sessionId = sessionId,
    onMessageCallback = onMessageCallback,
    sockets = JupyterWsClientSocketManager(DefaultKernelLoggerFactory)
        .open(jupyterParams)
)
