// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.execution.kernel.BaseJupyterKernelCommunicationClient
import com.intellij.jupyter.execution.kernel.JupyterMessageFilter
import com.intellij.jupyter.execution.process.KernelClientSession
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.ws.JupyterWsClientSocketManager

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
) : BaseJupyterKernelCommunicationClient<JupyterClientSockets>(
    sessionId = sessionId,
    onMessageCallback = onMessageCallback,
    sockets = JupyterWsClientSocketManager(DefaultKernelLoggerFactory)
        .open(jupyterParams)
)
