// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelPortsProvider
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelProcessFactory
import org.jetbrains.kotlinx.jupyter.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.startup.create
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.junit.Ignore
import org.junit.Test
import java.net.ServerSocket


class KotlinNotebookExecutionTest : AbstractSimpleExecutionTest() {
    @Test
    fun testExample1() = doTest(OutputsTester(listOf(
        listOf(
            textPlainOutput("5")
        ),
        listOf(),
        listOf(
            textPlainOutput("5")
        ),
        listOf()
    )))

    @Test
    fun testExampleWithBoundSocket() {
        val kernelProcessFactory = KernelRunnableFactory.EP.findExtensionOrFail(KernelProcessFactory::class.java)
        val defaultPortsProvider = kernelProcessFactory.kernelPortsProvider

        val openedSocket = ServerSocket(0)
        val boundPort = openedSocket.localPort
        val portsGenerator = PortsGenerator.create(32768, 65536)
        var attemptCount = 0
        val portsProvider = KernelPortsProvider {
            ++attemptCount
            createKernelPorts { socketType ->
                if (attemptCount == 1 && socketType == JupyterSocketType.HB) boundPort
                else portsGenerator.randomPort()
            }
        }

        try {
            kernelProcessFactory.setKernelPortsProvider(portsProvider)
            doTest(OutputsTester(listOf(
                listOf(
                    textPlainOutput("5")
                ),
                listOf(),
                listOf(
                    textPlainOutput("5")
                ),
                listOf()
            )))
            TestCase.assertTrue("Kernel restart was not attempted, attempts count: $attemptCount", attemptCount >= 2)
        } finally {
            openedSocket.close()
            kernelProcessFactory.setKernelPortsProvider(defaultPortsProvider)
        }
    }

    @Test
    fun testSerialization() = doTest(OutputsTester(listOf(
        listOf(),
        listOf(
            textPlainOutput("{\"x\":3}")
        ),
        listOf()
    )))

    @Ignore("Ignored because of some JCEF problems with project SDK")
    @Test
    fun testDataframe() = doTest(object: ReceivedMessagesTester {
        override val expectedCellsCount: Int get() = 3

        override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
            if (cellNum == 1) {
                val data = messages.outputs.single().messageData
                val html = data["text/html"].asText()
                assertTrue("DataFrame.renderTable" in html)
            }
        }
    })
}
