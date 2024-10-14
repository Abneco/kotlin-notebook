// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterSessionLaunchStrategy
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableProvider
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events.JupyterSessionVerifiedListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoRequest
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.seconds

abstract class JupyterSessionVerifiedLaunchStrategy(private val attemptsCount: Int) : JupyterSessionLaunchStrategy {
    companion object {
        /**
         * Topic about notifying what session is verified 
         */
        @Topic.ProjectLevel
        val TOPIC: Topic<JupyterSessionVerifiedLaunchStrategy> = Topic(JupyterSessionVerifiedLaunchStrategy::class.java, Topic.BroadcastDirection.NONE)
    }
    override suspend fun createAndVerifySession(jupyterClient: JupyterClient,
                                        sessionDataFactory: JupyterClient.() -> JupyterSessionData,
                                        sessionFactory: (JupyterSessionData) -> JupyterNotebookSession?): JupyterNotebookSession? {
        repeat(attemptsCount) {
            val sessionData = jupyterClient.sessionDataFactory()
            val session = sessionFactory(sessionData)

            val kernel = (jupyterClient as? KotlinKernelRunnableProvider)?.getKernel(sessionData.kernelId)

            if (verifySession(session, kernel)) {
                kernel?.markStarted()
                notifySessionVerified(session)
                return session
            }

            jupyterClient.deleteSession(sessionData.sessionId)
        }
        return null
    }

    private fun notifySessionVerified(session: JupyterNotebookSession) {
        val vFile = session.virtualFile ?: return

        ApplicationManager.getApplication().messageBus.syncPublisher(JupyterSessionVerifiedListener.TOPIC)
            .verifiedSessionStarting(session.project, vFile)
    }

    @OptIn(ExperimentalContracts::class)
    private suspend fun verifySession(
      session: JupyterNotebookSession?,
      kernel: KotlinKernelRunnableHandler?
    ): Boolean {
        contract {
            returns(true) implies (session != null)
        }
        if (session == null) return false

        val verificationDeferred = CompletableDeferred<Boolean>()

        kernel?.addBaseKernelListener(object: KotlinKernelListener {
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

        session.sendMessageOnPooledThread(zmqMessage, object : JupyterExecutionCallbackAdapter() {
            override fun onKernelInfoReply(message: JupyterMessage) {
                kernel?.onKernelInfoReply(message)
                verificationDeferred.complete(true)
            }
        })

        return withTimeoutOrNull(80.seconds) {
            verificationDeferred.await()
        } ?: false
    }
}
