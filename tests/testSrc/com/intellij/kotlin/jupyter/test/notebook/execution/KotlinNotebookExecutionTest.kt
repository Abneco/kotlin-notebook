// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelPortsProvider
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterKtScriptingSupport
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.test.runners.TestContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.waitForSmartMode
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.startup.create
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.jetbrains.plugins.notebooks.tests.withSwingMarkdownRenderMode
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
            doTest(
                OutputsTester(listOf(
                listOf(
                    textPlainOutput("5")
                ),
                listOf(),
                listOf(
                    textPlainOutput("5")
                ),
                listOf()
            ))
            )

            when (TestContext.kernelRunMode) {
                KotlinNotebookSessionRunMode.SEPARATE_PROCESS, KotlinNotebookSessionRunMode.ATTACHED_PROCESS -> {
                    assertTrue("Kernel restart was not attempted, attempts count: $attemptCount", attemptCount >= 2)
                }
                KotlinNotebookSessionRunMode.IDE_PROCESS -> {
                    assertTrue("Bound socket shouldn't be a problem for embedded kernel, but $attemptCount restart attempt(s) were made", attemptCount == 0)
                }
            }
        } finally {
            openedSocket.close()
            kernelProcessFactory.setKernelPortsProvider(defaultPortsProvider)
        }
    }

    @Test
    fun testSerialization() {
        withSwingMarkdownRenderMode {
            doTest(
                OutputsTester(listOf(
                listOf(),
                listOf(
                    buildJacksonObject {
                        replace("application/json", buildJacksonObject {
                            put("x", 3)
                        })
                        put("text/plain", "{\n    \"x\": 3\n}")
                        put("text/markdown", "```json\n{\n    \"x\": 3\n}\n```")
                    }
                ),
                listOf()
            )))
        }
    }

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

    // KTNB-552
    @Test
    fun testSetupInDefaultProject() {
        val project = ProjectManager.getInstance().defaultProject
        runBlocking {
            try {
                project.waitForSmartMode()
                JupyterKtScriptingSupport.updateSynchronously(project)
            } catch (e: Exception) {
                LOG.error("Test for default project has failed! ", e)
            }
        }
    }
}
