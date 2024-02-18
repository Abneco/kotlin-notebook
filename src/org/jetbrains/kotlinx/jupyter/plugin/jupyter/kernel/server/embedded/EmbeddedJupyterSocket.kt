// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.channel
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.toJupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.util.concurrent.atomic.AtomicReference

open class EmbeddedJupyterSocket(
    private val socketType: JupyterSocketType,
    private val onMessageCallback: (JupyterMessage) -> Unit,
    private val delayMs: Long = 500,
) : JupyterSocketBase {
    private val clientReply = AtomicReference<RawMessage?>(null)

    fun setClientReply(reply: RawMessage) {
        while (true) {
            if (clientReply.compareAndSet(null, reply)) break
            Thread.sleep(delayMs)
        }
    }

    override fun receiveRawMessage(): RawMessage {
        while (true) {
            val reply = clientReply.getAndSet(null)
            if (reply != null) {
                return reply
            }
            Thread.sleep(delayMs)
        }
    }

    override fun sendRawMessage(msg: RawMessage) {
        val message = msg.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }
}
