// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocket
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketInfo
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketManagerBase
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.protocol.createSocket
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.zeromq.ZMQ
import java.io.Closeable

class IdeaJupyterSocketManager(private val kernelConfig: KernelConfig): JupyterSocketManagerBase, Closeable {
    private val context = ZMQ.context(1)

    private fun openSocket(info: JupyterSocketInfo): JupyterSocket {
        val socket = createSocket(info, context, kernelConfig, JupyterSocketSide.IDE_CLIENT)
        if (info.type == JupyterSocketType.IOPUB) {
            socket.subscribe(byteArrayOf())
        }
        return socket
    }

    private val sockets = JupyterSocketInfo.values().associate { it.type to openSocket(it).apply { connect() } }

    override fun fromSocketType(type: JupyterSocketType): JupyterSocket {
        return sockets[type] ?: throw IllegalArgumentException("Unsupported socket type: $type")
    }

    override fun close() {
        sockets.values.forEach { it.close() }
        context.close()
    }
}