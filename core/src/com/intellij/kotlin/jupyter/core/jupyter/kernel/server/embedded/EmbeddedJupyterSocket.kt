// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.channel
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSendReceiveSocket
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import java.util.concurrent.ArrayBlockingQueue

/**
 * [EmbeddedJupyterSocket] emulates web-socket in the kernel embedded into the
 * current IDE process.
 * It calls message callbacks directly, without establishing any
 * over-the-web connection.
 *
 * @param socketType The type of Jupyter socket.
 * @param onMessageCallback The callback function to handle Jupyter messages coming
 * from kernel to the client.
 * Client sets the reply for a message and the moment kernel receives it.
 */
open class EmbeddedJupyterSocket(
    private val socketType: JupyterSocketType,
    private val onMessageCallback: (JupyterMessage) -> Unit,
) : JupyterSendReceiveSocket {
    private val clientReplyQueue = ArrayBlockingQueue<RawMessage>(10)

    /**
     * Set a reply for kernel on the client side
     */
    fun setClientReply(reply: RawMessage) {
        clientReplyQueue.add(reply)
    }

    /**
     * Receive a client message on the kernel side.
     * The notable use case of this is a standard input
     */
    override fun receiveRawMessage(): RawMessage {
        return clientReplyQueue.take()
    }

    /**
     * Send a message to a client from the kernel side.
     */
    override fun sendRawMessage(msg: RawMessage) {
        val message = msg.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }
}
