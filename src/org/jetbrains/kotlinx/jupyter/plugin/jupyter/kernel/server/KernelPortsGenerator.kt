// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class KernelPortsGenerator(
    private val portRangeStart: Int,
    private val portRangeEnd: Int,
) {
    init {
      assert(portRangeStart <= portRangeEnd) {
          "Wrong range limits were passed. Start: $portRangeStart, end $portRangeEnd"
      }
    }

    private val maxTrials = portRangeEnd - portRangeStart
    private val rng = Random()
    private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private fun isPortAvailable(port: Int): Boolean {
        var tcpSocket: ServerSocket? = null
        var udpSocket: DatagramSocket? = null
        try {
            tcpSocket = ServerSocket(port)
            tcpSocket.reuseAddress = true
            udpSocket = DatagramSocket(port)
            udpSocket.reuseAddress = true
            return true
        } catch (_: IOException) {
        } finally {
            tcpSocket?.close()
            udpSocket?.close()
        }
        return false
    }

    fun randomPort() =
        generateSequence { portRangeStart + rng.nextInt(portRangeEnd - portRangeStart) }.take(maxTrials).find {
            isPortAvailable(it) && usedPorts.add(it)
        } ?: throw RuntimeException("No free port found")
}
