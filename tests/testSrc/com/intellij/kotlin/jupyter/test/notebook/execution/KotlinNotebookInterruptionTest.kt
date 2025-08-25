// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.execution

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeListener
import com.intellij.jupyter.core.kernel.executor.JupyterKernelTaskExecutor
import com.intellij.jupyter.core.kernel.executor.JupyterTaskBaseCallback
import com.intellij.kotlin.jupyter.test.util.JDKVersionRule
import com.intellij.kotlin.jupyter.test.util.StopExecutionOnFailureRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.scientific.tables.utils.launchBackground
import com.intellij.testFramework.TestDataPath
import junit.framework.TestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import org.jetbrains.plugins.notebooks.tests.awaitBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/execution")
class KotlinNotebookInterruptionTest : AbstractSimpleExecutionTest() {
    @JvmField
    @Rule
    val jdkVersionRule = JDKVersionRule { it <= JavaSdkVersion.JDK_17 }

    @JvmField
    @Rule
    val stopExecutionOnFailureRule = StopExecutionOnFailureRule(false)

    // From JDK21+ it is no longer possible to interrupt at Thread from the outside.
    // It is only possible if the Tread itself is listening for the interrupt flag.
    @Test
    fun testInterruption() {
        val sessionDeferred = getSessionDeferred(project, testRootDisposable)
        val alreadyInterrupted = AtomicBoolean(false)
        doTest(object : ReceivedMessagesTester {
            override val expectedCellsCount: Int
                get() = 2

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {
                if (cellNum == 0) {
                    val msg = messages.outputs.single().messageContent
                    TestCase.assertEquals("error", msg["status"].asText())
                    TestCase.assertEquals("The execution was interrupted", msg["evalue"].asText())
                    LOG.debug("Execution was successfully interrupted")
                }
            }
        }, object : JupyterTaskBaseCallback() {
            override fun onStatus(message: JupyterStatusMessage) {
                if (message.executionState == JupyterExecutionState.BUSY && alreadyInterrupted.compareAndSet(false, true)) {
                    launchBackground {
                        delay(1000)
                        val session = sessionDeferred.awaitBlocking(5.seconds)
                        val file = session.virtualFile
                        JupyterKernelTaskExecutor.killExecutor(project, file)
                    }
                }
            }
        })
    }

    private fun getSessionDeferred(project: Project, disposable: Disposable): Deferred<JupyterNotebookSession> {
        val sessionDeferred = CompletableDeferred<JupyterNotebookSession>()
        JupyterRuntimeListener.register(disposable, object : JupyterRuntimeListener {
            override fun sessionCreated(session: JupyterNotebookSession) {
                sessionDeferred.complete(session)
            }
        })
        return sessionDeferred
    }

    companion object {
        private val LOG = logger<KotlinNotebookInterruptionTest>()
    }
}
