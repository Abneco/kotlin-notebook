// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.SocketWrapper
import org.jetbrains.kotlinx.jupyter.protocol.addressForSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ
import java.io.Closeable

class IdeaJupyterSocketManager(private val kernelConfig: KernelConfig): JupyterSocketManagerBase, Closeable {
    private val context = ZMQ.context(1)

    private fun createSocket(
        socketInfo: JupyterSocketInfo,
    ): JupyterSocket {
        val zmqSocket = context
            .socket(socketInfo.zmqType(JupyterSocketSide.IDE_CLIENT))
            .apply {
                linger = 0
            }
        return SocketWrapper(
            DefaultKernelLoggerFactory,
            socketInfo.name,
            zmqSocket,
            kernelConfig.addressForSocket(socketInfo),
            kernelConfig.hmac,
        )
    }

    private fun openSocket(info: JupyterSocketInfo): JupyterSocket {
        val socket = createSocket(info)
        if (info.type == JupyterSocketType.IOPUB) {
            socket.subscribe(byteArrayOf())
        }
        return socket
    }

    private val sockets = JupyterSocketInfo.entries.associate { it.type to openSocket(it).apply { connect() } }

    override fun fromSocketType(type: JupyterSocketType): JupyterSocket {
        return sockets[type] ?: throw IllegalArgumentException("Unsupported socket type: $type")
    }

    override fun close() {
        doClose()
    }

    private fun doClose() {
        sockets.values.forEach { it.closeSafely() }
        context.close()
    }
}
