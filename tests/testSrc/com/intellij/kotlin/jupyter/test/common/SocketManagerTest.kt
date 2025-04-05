// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.common

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.IdeaJupyterSocketManager
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.getWorkersFromContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.matchers.collections.shouldHaveSize
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.createClientKotlinKernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createRandomKernelPorts
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SocketManagerTest: BasePlatformTestCase() {
    @Test
    fun `socket manager should be successfully cleaned up`() {
        val kernelConfig = createClientKotlinKernelConfig(
            host = "*",
            ports = createRandomKernelPorts(),
            signatureKey = "zzz",
            replCompilerMode = ReplCompilerMode.DEFAULT
        )

        val socketManager = IdeaJupyterSocketManager(kernelConfig)
        val zmqContext = socketManager.getZmqContext()
        getWorkersFromContext(zmqContext).shouldHaveSize(1)

        socketManager.close()
        getWorkersFromContext(zmqContext).shouldHaveSize(0)
    }
}
