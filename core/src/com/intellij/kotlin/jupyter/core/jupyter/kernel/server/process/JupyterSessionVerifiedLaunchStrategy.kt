// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionLaunchStrategy
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_UPDATE_FILE_PATH_TIMEOUT
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
import org.jetbrains.kotlinx.jupyter.messaging.AbstractMessageContent
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataRequest
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import kotlin.io.path.absolute

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
        kernel: KotlinKernelRunnableHandler?
    ): Boolean {
        val verificationDeferred = CompletableDeferred<Boolean>()

        kernel?.addBaseKernelListener(object : KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                verificationDeferred.complete(false)
            }
        })

        val zmqMessage = session.makeShellMessage(
            MessageType.KERNEL_INFO_REQUEST,
            KernelInfoRequest()
        )

        val callback = object : FinalizationPreservingCallback(
            myFinalizeCallback = {
                verificationDeferred.complete(false)
            }
        ) {
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

    private suspend fun JupyterNotebookSession.updateNotebookMetadata(): Boolean {
        val replyDeferred = CompletableDeferred<Boolean>()

        val notebookFilePath = virtualFile.file.toNioPath().absolute()
        val zmqMessage = makeShellMessage(
            MessageType.UPDATE_CLIENT_METADATA_REQUEST,
            UpdateClientMetadataRequest(notebookFilePath)
        )

        val callback = object : FinalizationPreservingCallback(
            myFinalizeCallback = {
                replyDeferred.complete(false)
            }
        ) {
            override fun onStatus(message: JupyterStatusMessage) {
                super.onStatus(message)
                if (message.executionState == JupyterExecutionState.IDLE) {
                    replyDeferred.complete(true)
                }
            }
        }

        sendMessageOnPooledThread(zmqMessage, callback)

        val updateResult = withTimeoutOrNull(KERNEL_UPDATE_FILE_PATH_TIMEOUT) {
            replyDeferred.await()
        }
        notebookLogger().info("Kotlin Jupyter kernel session ${sessionId} update metadata result: $updateResult")
        return updateResult == true
    }

    private fun JupyterNotebookSession.makeShellMessage(
        messageType: MessageType,
        content: AbstractMessageContent,
    ): JupyterMessage {
        val messageFactory = NoReplyMessageFactory(sessionId)
        val message = messageFactory.makeSimpleMessage(messageType, content)
        return message.toRawMessage().toJupyterMessage(JupyterMessageChannel.SHELL)
    }

    private abstract class FinalizationPreservingCallback(
        private val myFinalizeCallback : () -> Unit,
    ): JupyterExecutionCallbackAdapter() {
        private var externalFinalizeCallback: () -> Unit = {}

        override var finalizeCallback: () -> Unit
            get() = {
                externalFinalizeCallback()
                myFinalizeCallback()
            }
            set(value) {
                externalFinalizeCallback = value
            }
    }
}
