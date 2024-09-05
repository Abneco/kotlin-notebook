// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.toJupyterMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.toRawMessageWithSocket
import org.jetbrains.kotlinx.jupyter.plugin.util.errorUnderDebug
import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageType
import org.zeromq.ZMQException
import java.nio.channels.ClosedSelectorException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class KernelZMQClientSession(
  override val sessionId: JupyterNotebookSessionId,
  kernelConfig: KernelConfig,
  private val onMessageCallback: (JupyterMessage) -> Unit
): AbstractJupyterConnection(), JupyterKernelCommunicationClient, KotlinKernelSession {
    private val receiveMessageLock = ReentrantLock(true)

    private val clientThreads: MutableList<Thread> = ContainerUtil.createConcurrentList()

    override val socketManager = IdeaJupyterSocketManager(kernelConfig)

    init {
        initSockets()
    }

    override fun send(content: JupyterMessage) {
        val messageType = content.header.messageType
        if (dontSendMessageOfType(messageType)) return

        val (rawMessage, socketType) = content.toRawMessageWithSocket() ?: return
        try {
            val socket = socketManager.fromSocketType(socketType)
            socket.sendRawMessage(rawMessage)
        } catch (e: Exception) {
            LOG.errorUnderDebug(e)
        }
    }

    private fun dontSendMessageOfType(messageType: JupyterMessageType): Boolean {
        return when (messageType) {
            // Don't send shutdown requests: kernel is killed by our own means anyway
            JupyterMessageType.SHUTDOWN_REQUEST -> true
            else -> false
        }
    }

    private fun initSockets() {
        fun socketLoop(
            interruptedMessage: String,
            loopBody: () -> Unit
        ) {
            while (true) {
                try {
                    loopBody()
                } catch (e: InterruptedException) {
                    LOG.debug(interruptedMessage)
                    break
                }
            }
        }

        val mainClientThread = thread(name = "Main Kernel ZMQ client thread") {
            val childThreads = buildList {
                JupyterSocketType.entries.forEach { socketType ->
                    val socket = socketManager.fromSocketType(socketType)

                    addMessageCallback(
                        rawMessageCallback(socketType, null) { rawMessage ->
                            receiveMessageLock.withLock {
                                processMessage(socketType, rawMessage)
                            }
                        }
                    )

                    add(
                        thread(name = "$socketType's socket thread") {
                            socketLoop("Socket $socketType: Interrupted") {
                                try {
                                    socket.runCallbacksOnMessage()
                                } catch (e: ClosedSelectorException) {
                                    rethrowAsInterrupted(e)
                                } catch (e: ZMQException) {
                                    rethrowAsInterrupted(e)
                                } catch (e: AssertionError) {
                                    rethrowAsInterrupted(e)
                                }
                            }
                        }
                    )
                }
            }

            clientThreads.addAll(childThreads)
            childThreads.forEach { it.join() }
        }
        clientThreads.add(mainClientThread)
    }

    private fun processMessage(socketType: JupyterSocketType, rawMessage: RawMessage) {
        val message = rawMessage.toJupyterMessage(socketType.channel)
        onMessageCallback(message)
    }


    override fun close() {
        clientThreads.clear()
        socketManager.closeSafely()
    }

    override fun dispose() {
        close()
    }

    companion object {
        private val LOG = logger<KernelZMQClientSession>()
    }
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