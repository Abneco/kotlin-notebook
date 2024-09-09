// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterExecutionInterruptService
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import junit.framework.TestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.kotlinx.jupyter.plugin.test.util.JDKVersionRule
import org.jetbrains.kotlinx.jupyter.plugin.test.util.StopExecutionOnFailureRule
import org.jetbrains.plugins.notebooks.tests.awaitBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds


class KotlinNotebookInterruptionTest : AbstractSimpleExecutionTest(){
    @JvmField
    @Rule
    val jdkVersionRule = JDKVersionRule { it <= JavaSdkVersion.JDK_17 }

    @JvmField
    @Rule
    val stopExecutionOnFailureRule = StopExecutionOnFailureRule(false)

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
        }, object : JupyterExecutionCallbackAdapter() {
            override fun onStatus(message: JupyterStatusMessage) {
                if (message.executionState == JupyterExecutionState.BUSY && alreadyInterrupted.compareAndSet(false, true)) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(1000)
                        val session = sessionDeferred.awaitBlocking(5.seconds)
                        val file = session.virtualFile ?: return@executeOnPooledThread
                        JupyterExecutionInterruptService.getInstance(project).interruptExecution(file)
                    }
                }
            }
        })
    }

    private fun getSessionDeferred(project: Project, disposable: Disposable): Deferred<JupyterNotebookSession> {
        val sessionDeferred = CompletableDeferred<JupyterNotebookSession>()
        project.messageBus.connect(disposable)
            .subscribe(JupyterRuntimeService.Listener.TOPIC, object : JupyterRuntimeService.Listener {
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
