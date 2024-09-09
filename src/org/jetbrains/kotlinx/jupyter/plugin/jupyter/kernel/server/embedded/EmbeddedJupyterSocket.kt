// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.channel
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketBase
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.toJupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import java.util.concurrent.atomic.AtomicReference

/**
 * [EmbeddedJupyterSocket] emulates web-socket in the kernel embedded into the
 * current IDE process.
 * It calls message callbacks directly, without establishing any
 * over-the-web connection.
 *
 * @param socketType The type of Jupyter socket.
 * @param onMessageCallback The callback function to handle Jupyter messages coming
 * from kernel to the client.
 * @param delayMs Maximum delay in milliseconds between the moment
 * client sets the reply for a message and the moment kernel receives it.
 */
open class EmbeddedJupyterSocket(
    private val socketType: JupyterSocketType,
    private val onMessageCallback: (JupyterMessage) -> Unit,
    private val delayMs: Long = 500,
) : JupyterSocketBase {
    private val clientReply = AtomicReference<RawMessage?>(null)

    /**
     * Set a reply for kernel on the client side
     */
    fun setClientReply(reply: RawMessage) {
        while (true) {
            // We don't set the new reply until the kernel reads the old one
            if (clientReply.compareAndSet(null, reply)) break
            Thread.sleep(delayMs)
        }
    }

    /**
     * Receive a client message on the kernel side.
     * The notable use case of this is a standard input
     */
    override fun receiveRawMessage(): RawMessage {
        while (true) {
            val reply = clientReply.getAndSet(null)
            if (reply != null) {
                return reply
            }
            Thread.sleep(delayMs)
        }
    }

    /**
     * Send a message to a client from the kernel side.
     */
    override fun sendRawMessage(msg: RawMessage) {
        val message = msg.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }
}
