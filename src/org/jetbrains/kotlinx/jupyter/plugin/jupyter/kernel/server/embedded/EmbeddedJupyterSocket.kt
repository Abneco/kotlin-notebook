// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.channel
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.toJupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage

class EmbeddedJupyterSocket(
    private val socketType: JupyterSocketType,
    private val onMessageCallback: (JupyterMessage) -> Unit
) : JupyterSocketBase {
    override fun receiveRawMessage(): RawMessage? {
        return null
    }

    override fun sendRawMessage(msg: RawMessage) {
        val message = msg.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }
}
