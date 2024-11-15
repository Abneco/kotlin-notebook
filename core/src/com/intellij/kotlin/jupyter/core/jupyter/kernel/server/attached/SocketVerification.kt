// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
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

fun KernelConfig.areAllSocketsOpen(): Boolean {
    val myHost = host.takeIf { it != "*" } ?: "localhost"
    return ports.values.all { isSocketOpen(myHost, it) }
}
