// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionLaunchStrategy
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_VERIFICATION_ATTEMPT_COUNT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_VERIFICATION_ATTEMPT_TIMEOUT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinInProcessJupyterClient
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableProvider
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events.JupyterSessionVerifiedListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.FinalizationPreservingCallback
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.sendMessageAndWait
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.updateNotebookMetadata
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageType

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

            if (verifySession(session, kernel, sessionData.kernelId)) {
                session.updateNotebookMetadata()
                kernel?.markVerified()
                notifySessionVerified(session)
                return session
            }

            // Let's wait for a proper session cleanup to ensure state consistency
            (jupyterClient as KotlinInProcessJupyterClient)
                .deleteSessionAndWaitForTermination(sessionData.sessionId)
            session.deleteSession()
        }
        return null
    }

    private fun notifySessionVerified(session: JupyterNotebookSession) {
        val vFile = session.virtualFile

        ApplicationManager.getApplication().messageBus.syncPublisher(JupyterSessionVerifiedListener.TOPIC)
            .verifiedSessionStarting(session.project, vFile)
    }

    private suspend fun verifySession(
        session: JupyterNotebookSession,
        kernel: KotlinKernelRunnableHandler?,
        kernelId: JupyterKernelId,
    ): Boolean {
        repeat(KERNEL_VERIFICATION_ATTEMPT_COUNT) { attemptCounter ->
            if (attemptCounter > 0) {
                val kernelClientSession = (session.jupyterServer.client as KotlinInProcessJupyterClient)
                    .getKernelSession(kernelId) as? KernelClientSession
                kernelClientSession?.restartCommunication()
            }
            val verificationResult = session.sendMessageAndWait(
                channel = JupyterMessageChannel.SHELL,
                messageType = MessageType.KERNEL_INFO_REQUEST,
                content = KernelInfoRequest(),
                timeout = KERNEL_VERIFICATION_ATTEMPT_TIMEOUT
            ) { verificationDeferred ->
                kernel?.addBaseKernelListener(object : KotlinKernelListener {
                    override fun kernelTerminated(event: KotlinKernelEvent) {
                        verificationDeferred.complete(false)
                    }
                })

                object : FinalizationPreservingCallback(
                    myFinalizeCallback = {
                        verificationDeferred.complete(false)
                    }
                ) {
                    override fun onKernelInfoReply(message: JupyterMessage) {
                        kernel?.onKernelInfoReply(message)
                        verificationDeferred.complete(true)
                    }
                }
            }
            if (verificationResult == true) return true
        }
        return false
    }
}
