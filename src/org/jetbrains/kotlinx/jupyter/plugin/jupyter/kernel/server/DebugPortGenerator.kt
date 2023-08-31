// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.plugin.util.UniqueGenerator
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.random.nextUInt

class DebugPortGenerator: UniqueGenerator<UInt, Int>() {
    override fun UInt.asResult(): Int = toInt()

    private fun isFreeLocally(port: Int): Boolean {
        try {
          ServerSocket(port).use {
              return true
          }
        } catch (e: Exception) {
            return false
        }
    }

    override fun next(): UInt {
        var port: UInt
        do {
            port = Random.nextUInt(LOWER_BOUND, UPPER_BOUND - LOWER_BOUND)
        } while (!isFreeLocally(port.toInt()))

        return port
    }


    companion object {
        private const val UPPER_BOUND: UInt = 65535u
        private const val LOWER_BOUND: UInt = 8000u
    }
}