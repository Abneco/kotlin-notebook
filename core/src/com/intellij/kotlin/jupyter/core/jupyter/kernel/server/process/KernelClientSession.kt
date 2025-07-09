// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.SESSION_KILL_WAIT_TIMEOUT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.JupyterMessageFilter
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toRawMessageWithSocket
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.ui.EDT
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.JupyterClientSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterZmqClientSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.JupyterZmqClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.jetbrains.kotlinx.jupyter.ws.JupyterWsClientSocketManager
import org.zeromq.ZMQException
import java.nio.channels.ClosedSelectorException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

sealed class KernelClientSession(
    override val sessionId: JupyterNotebookSessionId,
    kernelConfig: KernelConfig,
    private val onMessageCallback: (JupyterMessage) -> Unit,
    private val outgoingMessagesFilter: JupyterMessageFilter,
    socketManager: JupyterClientSocketManager,
) : JupyterKernelCommunicationClient, KotlinKernelSession {
    private val receiveMessageLock = ReentrantLock(true)
    private val isClosing = AtomicBoolean(false)
    protected val sockets: JupyterClientSockets = socketManager.open(kernelConfig)

    init {
        initSockets()
    }

    override fun send(content: JupyterMessage) {
        if (!outgoingMessagesFilter.accepts(content)) return
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

    override fun dispose() {
        close()
    }

    protected open fun closeSockets() {
        closeWithTimeout(timeoutMs = SESSION_KILL_WAIT_TIMEOUT.inWholeMilliseconds) {
            sockets.close()
        }
    }

    override fun close() {
        if (!isClosing.compareAndSet(false, true)) return

        val closeDeferred = KotlinNotebookPluginScope.global.launch {
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

class KernelZmqClientSession(
    sessionId: JupyterNotebookSessionId,
    kernelConfig: KernelConfig,
    onMessageCallback: (JupyterMessage) -> Unit,
    outgoingMessagesFilter: JupyterMessageFilter,
) : KernelClientSession(
    sessionId = sessionId,
    kernelConfig = kernelConfig,
    onMessageCallback = onMessageCallback,
    outgoingMessagesFilter = outgoingMessagesFilter,
    socketManager = JupyterZmqClientSocketManager(DefaultKernelLoggerFactory, JupyterSocketSide.IDE_CLIENT)
) {
    override fun closeSockets() {
        super.closeSockets()
        for (zmqPoller in getPollersFromContext((sockets as JupyterZmqClientSockets).context)) {
            if (zmqPoller.workerThread?.isAlive != false) {
                notebookLogger().warn("Undisposed ZMQ Poller $zmqPoller detected, interrupting polling")
                zmqPoller.closeSafely()
            }
        }
    }
}

class KernelWsClientSession(
    sessionId: JupyterNotebookSessionId,
    kernelConfig: KernelConfig,
    onMessageCallback: (JupyterMessage) -> Unit,
    outgoingMessagesFilter: JupyterMessageFilter,
) : KernelClientSession(
    sessionId = sessionId,
    kernelConfig = kernelConfig,
    onMessageCallback = onMessageCallback,
    outgoingMessagesFilter = outgoingMessagesFilter,
    socketManager = JupyterWsClientSocketManager(DefaultKernelLoggerFactory)
)

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