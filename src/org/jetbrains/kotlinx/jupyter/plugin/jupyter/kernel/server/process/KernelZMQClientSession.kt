// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.api.libraries.rawMessageCallback
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.util.errorUnderDebug
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinSerializationJson
import org.jetbrains.kotlinx.jupyter.protocol.AbstractJupyterConnection
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageImpl
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
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

    private val messageBytePrefix = listOf(byteArrayOf(1))

    private val clientThreads: MutableList<Thread> = ContainerUtil.createConcurrentList()

    override val socketManager = IdeaJupyterSocketManager(kernelConfig)

    init {
        initSockets()
    }

    override fun send(content: JupyterMessage) {
        val socketType = content.channel.socketType ?: return
        val socket = socketManager.fromSocketType(socketType)

        try {
            socket.sendRawMessage(RawMessageImpl(
                messageBytePrefix,
                content.header.json.toKotlinSerializationJson().jsonObject,
                content.parentHeader?.json?.toKotlinSerializationJson()?.jsonObject,
                null,
                content.messageContent.toKotlinSerializationJson()
            ))
        } catch (e: Exception) {
            LOG.errorUnderDebug(e)
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
        val message = createZMQJupyterMessage(socketType.channel, rawMessage)
        onMessageCallback(message)
    }


    override fun close() {
        socketManager.closeSafely()
        clientThreads.clear()
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