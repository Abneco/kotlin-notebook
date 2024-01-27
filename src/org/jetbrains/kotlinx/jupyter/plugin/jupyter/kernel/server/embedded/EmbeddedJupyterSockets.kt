// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage

class EmbeddedJupyterSockets(
    private val onMessageCallback: (JupyterMessage) -> Unit
) : JupyterBaseSockets {
    private fun openSocket(socketInfo: JupyterSocketInfo) = EmbeddedJupyterSocket(
        socketInfo.type,
        onMessageCallback
    )

    override val heartbeat = openSocket(JupyterSocketInfo.HB)
    override val shell = openSocket(JupyterSocketInfo.SHELL)
    override val control = openSocket(JupyterSocketInfo.CONTROL)
    override val stdin = openSocket(JupyterSocketInfo.STDIN)
    override val iopub = openSocket(JupyterSocketInfo.IOPUB)
}
