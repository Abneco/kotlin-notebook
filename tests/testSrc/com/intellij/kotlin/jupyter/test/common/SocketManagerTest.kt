// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.common

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.IdeaJupyterSocketManager
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.getPollersFromContext
import com.intellij.kotlin.jupyter.test.KotlinNotebookUnitTestCase
import com.intellij.testFramework.common.waitUntil
import io.kotest.common.runBlocking
import io.kotest.matchers.collections.shouldHaveSize
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.createClientKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createRandomKernelPorts
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SocketManagerTest: KotlinNotebookUnitTestCase() {
    @Test
    fun `socket manager should be successfully cleaned up`() {
        withSocketManager { socketManager ->
            val zmqContext = socketManager.getZmqContext()
            getPollersFromContext(zmqContext).shouldHaveSize(1)

            socketManager.close()
            getPollersFromContext(zmqContext).shouldHaveSize(0)
        }
    }

    @Test
    fun `socket manager's poller thread should die when ZMQ poller is closed`() {
        withSocketManager { socketManager ->
            val zmqContext = socketManager.getZmqContext()
            val pollers = getPollersFromContext(zmqContext).shouldHaveSize(1)
            val poller = pollers.single()
            poller.close()

            runBlocking {
                waitUntil("Poller thread was not killed", 30.seconds) {
                    poller.workerThread?.isAlive == false
                }
            }
        }
    }

    private fun withSocketManager(action: (IdeaJupyterSocketManager) -> Unit) {
        val kernelConfig = createClientKotlinKernelConfig(
            host = "*",
            ports = createRandomKernelPorts(),
            signatureKey = "zzz",
            replCompilerMode = ReplCompilerMode.DEFAULT
        )

        IdeaJupyterSocketManager(kernelConfig).use(action)
    }

    override fun runInDispatchThread(): Boolean = false
}
