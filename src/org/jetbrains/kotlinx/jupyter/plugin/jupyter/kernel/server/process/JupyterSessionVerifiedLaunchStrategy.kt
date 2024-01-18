// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableProvider
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterSessionData
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterSessionLaunchStrategy
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

abstract class JupyterSessionVerifiedLaunchStrategy(private val attemptsCount: Int) : JupyterSessionLaunchStrategy {
    override fun createAndVerifySession(jupyterClient: JupyterClient,
                                        sessionDataFactory: JupyterClient.() -> JupyterSessionData,
                                        sessionFactory: (JupyterSessionData) -> JupyterNotebookSession?): JupyterNotebookSession? {
        repeat(attemptsCount) {
            val sessionData = jupyterClient.sessionDataFactory()
            val session = sessionFactory(sessionData)

            val kernel = (jupyterClient as? KotlinKernelRunnableProvider)?.getKernel(sessionData.kernelId)

            if (verifySession(session, kernel)) {
                kernel?.markStarted()
                return session
            }

            jupyterClient.deleteSession(sessionData.sessionId)
        }
        return null
    }

    @OptIn(ExperimentalContracts::class)
    private fun verifySession(
        session: JupyterNotebookSession?,
        kernel: KotlinKernelRunnableHandler?
    ): Boolean {
        contract {
            returns(true) implies (session != null)
        }
        if (session == null) return false

        val verificationFuture = CompletableFuture<Boolean>()

        kernel?.addKernelListener(object: KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                verificationFuture.complete(false)
            }
        })

        val messageFactory = NoReplyMessageFactory(session.sessionId)
        val message = messageFactory.makeSimpleMessage(
            MessageType.KERNEL_INFO_REQUEST,
            KernelInfoRequest()
        )
        val zmqMessage = createZMQJupyterMessage(JupyterMessageChannel.SHELL, message.toRawMessage())

        session.sendMessage(zmqMessage, object : JupyterExecutionCallbackAdapter() {
            override fun onKernelInfoReply(message: JupyterMessage) {
                verificationFuture.complete(true)
            }
        })

        return try {
            verificationFuture.get(120, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            false
        }
    }
}
