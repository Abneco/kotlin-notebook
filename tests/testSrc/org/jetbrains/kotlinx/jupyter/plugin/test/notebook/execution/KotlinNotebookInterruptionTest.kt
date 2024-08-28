// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.util.JDKVersionRule
import org.jetbrains.kotlinx.jupyter.plugin.test.util.StopExecutionOnFailureRule
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionInterruptService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.notebook.JupyterRuntimeService
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class KotlinNotebookInterruptionTest : AbstractSimpleExecutionTest(){
    @JvmField
    @Rule
    val jdkVersionRule = JDKVersionRule { it <= JavaSdkVersion.JDK_17 }

    @JvmField
    @Rule
    val stopExecutionOnFailureRule = StopExecutionOnFailureRule(false)

    @Test
    fun testInterruption() {
        val sessionFuture = getSessionFuture(project, testRootDisposable)
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
        }, object : JupyterExecutionCallbackAdapter() {
            override fun onStatus(message: JupyterStatusMessage) {
                if (message.executionState == JupyterExecutionState.BUSY && alreadyInterrupted.compareAndSet(false, true)) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(1000)
                        val session = sessionFuture.get(5, TimeUnit.SECONDS)
                        val file = session.virtualFile ?: return@executeOnPooledThread
                        JupyterExecutionInterruptService.getInstance(project).interruptExecution(file)
                    }
                }
            }
        })
    }

    private fun getSessionFuture(project: Project, disposable: Disposable): Future<JupyterNotebookSession> {
        val sessionFuture = CompletableFuture<JupyterNotebookSession>()
        project.messageBus.connect(disposable)
            .subscribe(JupyterRuntimeService.Listener.TOPIC, object : JupyterRuntimeService.Listener {
                override fun sessionCreated(session: JupyterNotebookSession) {
                    sessionFuture.complete(session)
                }
            })
        return sessionFuture
    }

    companion object {
        private val LOG = logger<KotlinNotebookInterruptionTest>()
    }
}
