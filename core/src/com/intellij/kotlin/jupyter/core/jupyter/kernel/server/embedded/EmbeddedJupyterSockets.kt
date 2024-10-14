// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo

/**
 * Holds all Jupyter sockets that are needed for messaging
 * between the kernel and the client inside the IDE process
 *
 * @param onMessageCallback Called when the kernel sends something to one of the sockets.
 * Socket type is reflected in [JupyterMessage.channel]
 */
class EmbeddedJupyterSockets(
    private val onMessageCallback: (JupyterMessage) -> Unit
) : JupyterBaseSockets {
    private fun openSocket(socketInfo: JupyterSocketInfo) =
      EmbeddedJupyterSocket(
        socketInfo.type,
        onMessageCallback
      )

    override val heartbeat = openSocket(JupyterSocketInfo.HB)
    override val shell = openSocket(JupyterSocketInfo.SHELL)
    override val control = openSocket(JupyterSocketInfo.CONTROL)
    override val stdin = openSocket(JupyterSocketInfo.STDIN)
    override val iopub = openSocket(JupyterSocketInfo.IOPUB)
}
