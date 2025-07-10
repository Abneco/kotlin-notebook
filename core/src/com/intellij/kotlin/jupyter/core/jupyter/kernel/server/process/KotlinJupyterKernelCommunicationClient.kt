// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.SESSION_KILL_WAIT_TIMEOUT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toRawMessageWithSocket
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.ui.EDT
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.zeromq.ZMQException
import java.nio.channels.ClosedSelectorException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal sealed class KotlinJupyterKernelCommunicationClient<S : JupyterClientSockets>(
    val sessionId: JupyterNotebookSessionId,
    private val onMessageCallback: (JupyterMessage) -> Unit,
    protected val sockets: S,
) : JupyterKernelCommunicationClient {
    private val receiveMessageLock = ReentrantLock(true)
    private val isClosing = AtomicBoolean(false)
    init {
        initSockets()
    }

    override fun send(content: JupyterMessage) {
        val (rawMessage, socketType) = content.toRawMessageWithSocket() ?: return
        try {
            val socket = sockets.fromSocketType(socketType)
                ?: throw IllegalArgumentException("Interacting with heartbeat socket is not supported")
            LOG.debug { "Sending message to $socketType in $sessionId:\n$rawMessage" }
            socket.sendRawMessage(rawMessage)
        } catch (e: Exception) {
            LOG.warn(e)
        }
    }

    private fun initSockets() {
        JupyterSocketType.entries.forEach { socketType ->
            val socket = sockets.fromSocketType(socketType)

            socket?.onRawMessage { rawMessage ->
                try {
                    receiveMessageLock.withLock { processMessage(socketType, rawMessage) }
                } catch (e: ClosedSelectorException) {
                    rethrowAsInterrupted(e)
                } catch (e: ZMQException) {
                    rethrowAsInterrupted(e)
                } catch (e: AssertionError) {
                    rethrowAsInterrupted(e)
                }
            }
        }
    }

    private fun processMessage(socketType: JupyterSocketType, rawMessage: RawMessage) {
        val message = rawMessage.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }

    protected open fun closeSockets() {
      closeWithTimeout(timeoutMs = SESSION_KILL_WAIT_TIMEOUT.inWholeMilliseconds) {
        sockets.close()
      }
    }

    override fun close() {
        if (!isClosing.compareAndSet(false, true)) return

        val closeDeferred = KotlinNotebookPluginScope.Companion.global.launch {
            closeSockets()
        }

        if (!EDT.isCurrentThreadEdt()) {
          runBlockingMaybeCancellable {
            closeDeferred.join()
          }
        }
    }

    companion object {
        private val LOG = KotlinNotebookLoggerFactory.getInstance(KernelClientSession::class)
    }
}

private fun JupyterClientSockets.fromSocketType(socketType: JupyterSocketType) = when (socketType) {
    JupyterSocketType.SHELL -> shell
    JupyterSocketType.CONTROL -> control
    JupyterSocketType.STDIN -> stdin
    JupyterSocketType.IOPUB -> ioPub
    JupyterSocketType.HB -> null
}

val JupyterMessageChannel.socketType: JupyterSocketType? get() {
    return when(this) {
        JupyterMessageChannel.SHELL -> JupyterSocketType.SHELL
        JupyterMessageChannel.IOPUB -> JupyterSocketType.IOPUB
        JupyterMessageChannel.STDIN -> JupyterSocketType.STDIN
        JupyterMessageChannel.CONTROL -> JupyterSocketType.CONTROL
        JupyterMessageChannel.HEARTBEAT -> JupyterSocketType.HB
        JupyterMessageChannel.NONE -> null
        JupyterMessageChannel.ANY -> null
    }
}

val JupyterSocketType.channel: JupyterMessageChannel get() {
    return when(this) {
        JupyterSocketType.HB -> JupyterMessageChannel.HEARTBEAT
        JupyterSocketType.SHELL -> JupyterMessageChannel.SHELL
        JupyterSocketType.CONTROL -> JupyterMessageChannel.CONTROL
        JupyterSocketType.STDIN -> JupyterMessageChannel.STDIN
        JupyterSocketType.IOPUB -> JupyterMessageChannel.IOPUB
    }
}
