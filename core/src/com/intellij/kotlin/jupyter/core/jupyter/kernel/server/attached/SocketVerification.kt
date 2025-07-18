// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import org.jetbrains.kotlinx.jupyter.zmq.protocol.ZmqKernelPorts
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.ws.WsKernelPorts
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

fun isSocketOpen(host: String, port: Int): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2000)
            true
        }
    } catch (e: IOException) {
        false
    }
}

fun KernelJupyterParams.areAllSocketsOpen(): Boolean {
    val myHost = host.takeIf { it != ANY_HOST_NAME } ?: "localhost"
    return when (val ports = ports) {
        is ZmqKernelPorts -> ports.ports.values.all { isSocketOpen(myHost, it) }
        is WsKernelPorts -> isSocketOpen(myHost, ports.port)
        else -> error("Unknown kernel ports type: $ports")
    }
}
