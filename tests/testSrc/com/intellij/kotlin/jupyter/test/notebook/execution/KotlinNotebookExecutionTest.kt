// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelPortsProvider
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KernelProcessFactory
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.ScriptingUpdateMode
import com.intellij.kotlin.jupyter.test.runners.RunModeAwareTest
import com.intellij.kotlin.jupyter.test.runners.TestContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.startup.create
import org.jetbrains.kotlinx.jupyter.startup.ZmqKernelPorts
import org.junit.Ignore
import org.junit.Test
import java.net.ServerSocket


@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/execution")
@RunModeAwareTest
class KotlinNotebookExecutionTest : KotlinNotebookTestCase() {

    @Test
    fun example1() = runNotebookTest {
        executeCell(0).assertOutput(textPlainOutput("5"))
        executeCell(1).assertOutput(emptyOutput())
        executeCell(2).assertOutput(textPlainOutput("5"))
        executeCell(3).assertOutput(emptyOutput())
    }

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
            ZmqKernelPorts { socketType ->
                if (attemptCount == 1 && socketType == JupyterSocketType.HB) boundPort
                else portsGenerator.randomPort()
            }
        }

        try {
            kernelProcessFactory.setKernelPortsProvider(portsProvider)
            runNotebookTest {
                executeCell(0).assertOutput(textPlainOutput("5"))
                executeCell(1).assertOutput(emptyOutput())
                executeCell(2).assertOutput(textPlainOutput("5"))
                executeCell(3).assertOutput(emptyOutput())
            }

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
    fun serialization() = runNotebookTest {
        executeCell(0).assertOutput(emptyOutput())
        executeCell(1).let { result ->
            val expectedOutput = buildJacksonObject {
                replace("application/json", buildJacksonObject {
                    put("x", 3)
                })
                put("text/plain", "{\n    \"x\": 3\n}")
                put("text/markdown", "```json\n{\n    \"x\": 3\n}\n```")
            }
            result.assertOutput(expectedOutput)
        }
        executeCell(2).assertOutput(emptyOutput())
    }

    @Ignore("Ignored because of some JCEF problems with project SDK")
    @Test
    fun dataframe() = runNotebookTest {
        executeCell(0)
        executeCell(1).let { result ->
            val html = result.output["text/html"].asText()
            assertTrue("DataFrame.renderTable" in html)
        }
    }


    // Test for KTNB-552
    @Ignore("Ignored, because it doesn't actually test anything in the old implementation, and is now broken with the new dependency update logic")
    @Test
    fun setupInDefaultProject() {
        val project = ProjectManager.getInstance().defaultProject
        runBlocking {
            try {
                project.waitForSmartMode()
                setUpDependenciesSynchronously(
                    0,
                    updateMode = ScriptingUpdateMode.FileAgnostic
                )
            } catch (e: Exception) {
                LOG.error("Test for default project has failed! ", e)
            }
        }
    }
}
