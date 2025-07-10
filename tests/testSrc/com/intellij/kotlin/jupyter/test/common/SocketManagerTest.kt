// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.common

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.closeSafely
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.getPollersFromContext
import com.intellij.kotlin.jupyter.test.KotlinNotebookUnitTestCase
import com.intellij.testFramework.common.waitUntil
import io.kotest.common.runBlocking
import io.kotest.matchers.collections.shouldHaveSize
import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.messaging.JupyterZmqClientSocketManager
import org.jetbrains.kotlinx.jupyter.messaging.JupyterZmqClientSockets
import org.jetbrains.kotlinx.jupyter.protocol.JupyterSocketSide
import org.jetbrains.kotlinx.jupyter.startup.createClientKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createRandomZmqKernelPorts
import org.jetbrains.kotlinx.jupyter.util.closeWithTimeout
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SocketManagerTest : KotlinNotebookUnitTestCase() {
    @Test
    fun `socket manager should be successfully cleaned up`() {
        val zmqSockets = openZmqSockets()
        val zmqContext = zmqSockets.context
        getPollersFromContext(zmqContext).shouldHaveSize(2)

        zmqSockets.close()

        runBlocking {
            waitUntil("ZMQ threads leaked", 30.seconds) {
                val pollers = getPollersFromContext(zmqContext)
                pollers.all { poller -> poller.workerThread?.isAlive == false }
            }
        }
    }

    @Test
    fun `socket manager's poller thread should die when ZMQ poller is closed`() {
        withZmqSockets { zmqSockets ->
            val zmqContext = zmqSockets.context
            val pollers = getPollersFromContext(zmqContext).shouldHaveSize(2)
            for (poller in pollers) {
                poller.closeSafely()
            }

            runBlocking {
                waitUntil("Poller threads were not killed", 30.seconds) {
                    pollers.all { poller -> poller.workerThread?.isAlive == false }
                }
            }
            println("done!")
        }
    }

    private fun withZmqSockets(action: (JupyterZmqClientSockets) -> Unit) {
        val sockets = openZmqSockets()
        try {
            action(sockets)
        } finally {
          closeWithTimeout(10_000L) {
              sockets.close()
          }
        }
    }

    private fun openZmqSockets(): JupyterZmqClientSockets {
        val kernelConfig = createClientKotlinKernelConfig(
            host = "*",
            ports = createRandomZmqKernelPorts(),
            signatureKey = "zzz",
            replCompilerMode = ReplCompilerMode.DEFAULT,
            extraCompilerArgs = emptyList(),
        )

        return JupyterZmqClientSocketManager(DefaultKernelLoggerFactory, side = JupyterSocketSide.IDE_CLIENT)
            .open(kernelConfig)
    }

    override fun runInDispatchThread(): Boolean = false
}
