// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionLaunchStrategy
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_VERIFICATION_TIMEOUT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinInProcessJupyterClient
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableProvider
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events.JupyterSessionVerifiedListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage

abstract class JupyterSessionVerifiedLaunchStrategy(private val attemptsCount: Int) : JupyterSessionLaunchStrategy {
    override suspend fun createAndVerifySession(
        jupyterClient: JupyterClient,
        sessionDataFactory: suspend JupyterClient.() -> JupyterSessionData,
        sessionFactory: (JupyterSessionData) -> JupyterNotebookSession?
    ): JupyterNotebookSession? {
        repeat(attemptsCount) {
            val sessionData = jupyterClient.sessionDataFactory()
            val session = sessionFactory(sessionData) ?: return null

            val kernel = (jupyterClient as? KotlinKernelRunnableProvider)?.getKernel(sessionData.kernelId)

            if (verifySession(session, kernel)) {
                kernel?.markVerified()
                notifySessionVerified(session)
                return session
            }

            // Let's wait for a proper session cleanup to ensure state consistency
            (jupyterClient as KotlinInProcessJupyterClient)
                .deleteSessionAndWaitForTermination(sessionData.sessionId)
        }
        return null
    }

    private fun notifySessionVerified(session: JupyterNotebookSession) {
        val vFile = session.virtualFile ?: return

        ApplicationManager.getApplication().messageBus.syncPublisher(JupyterSessionVerifiedListener.TOPIC)
            .verifiedSessionStarting(session.project, vFile)
    }

    private suspend fun verifySession(
        session: JupyterNotebookSession,
        kernel: KotlinKernelRunnableHandler?
    ): Boolean {
        val verificationDeferred = CompletableDeferred<Boolean>()

        kernel?.addBaseKernelListener(object : KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                verificationDeferred.complete(false)
            }
        })

        val messageFactory = NoReplyMessageFactory(session.sessionId)
        val message = messageFactory.makeSimpleMessage(
            MessageType.KERNEL_INFO_REQUEST,
            KernelInfoRequest()
        )
        val zmqMessage = message.toRawMessage().toJupyterMessage(JupyterMessageChannel.SHELL)

        val callback = object : JupyterExecutionCallbackAdapter() {
            private val myFinalizeCallback = {
                verificationDeferred.complete(false)
            }

            private var externalFinalizeCallback: () -> Unit = {}

            override var finalizeCallback: () -> Unit
                get() = {
                    externalFinalizeCallback()
                    myFinalizeCallback()
                }
                set(value) {
                    externalFinalizeCallback = value
                }

            override fun onKernelInfoReply(message: JupyterMessage) {
                kernel?.onKernelInfoReply(message)
                verificationDeferred.complete(true)
            }
        }

        session.sendMessageOnPooledThread(zmqMessage, callback)
        notebookLogger().info("Sending info_request to verify Kotlin Jupyter kernel session ${session.sessionId}")

        val verificationResult = withTimeoutOrNull(KERNEL_VERIFICATION_TIMEOUT) {
            verificationDeferred.await()
        }
        notebookLogger().info("Kotlin Jupyter kernel session ${session.sessionId} verification result: $verificationResult")

        return verificationResult == true
    }
}
