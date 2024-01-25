// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelPortsProvider
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KernelProcessFactory
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.executeCellsAndShutdownKernel
import org.jetbrains.kotlinx.jupyter.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.startup.create
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.junit.Ignore
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class KotlinNotebookExecutionTest : KotlinNotebookExecutionBaseTestCase() {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/execution"

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

    @Test
    fun testInterruption() {
        val futureSession = getFutureSession(project, testRootDisposable)
        val alreadyInterrupted = AtomicBoolean(false)
        doTest(object : ReceivedMessagesTester {
            override val expectedCellsCount: Int
                get() = 2

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                if (cellNum == 0) {
                    val output = messages.outputs.single().messageContent
                    TestCase.assertEquals("stderr", output["name"].asText())
                    TestCase.assertEquals("The execution was interrupted", output["text"].asText())
                    log.debug("Execution was successfully interrupted")
                }
            }
        }, object : JupyterExecutionCallbackAdapter() {
            override fun onStatus(message: JupyterStatusMessage) {
                if (message.executionState == JupyterExecutionState.BUSY && alreadyInterrupted.compareAndSet(false, true)) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(1000)
                        val session = futureSession.get(5, TimeUnit.SECONDS)
                        val file = session.virtualFile ?: return@executeOnPooledThread
                        JupyterCellExecutionManager.getInstance(project).interruptJupyterKernel(file)
                    }
                }
            }
        })
    }

    private fun doTest(tester: ReceivedMessagesTester, executionCallback: JupyterExecutionCallback? = null) {
        val notebookFile = configureExecutionTest()
        executeCellsAndShutdownKernel(tester, notebookFile, executionCallback)
    }

    companion object {
        val log = logger<KotlinNotebookExecutionTest>()

        private fun getFutureSession(project: Project, disposable: Disposable): Future<JupyterNotebookSession> {
            val futureSession = CompletableFuture<JupyterNotebookSession>()
            project.messageBus.connect(disposable)
                .subscribe(JupyterRuntimeService.Listener.TOPIC, object : JupyterRuntimeService.Listener {
                    override fun sessionCreated(session: JupyterNotebookSession) {
                        futureSession.complete(session)
                    }
                })
            return futureSession
        }
    }
}
