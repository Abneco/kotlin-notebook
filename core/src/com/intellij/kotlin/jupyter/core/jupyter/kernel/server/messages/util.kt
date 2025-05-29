// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterExecutionState
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterStatusMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KERNEL_UPDATE_FILE_PATH_TIMEOUT
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.NoReplyMessageFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.toJupyterMessage
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.kotlinx.jupyter.messaging.AbstractMessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataRequest
import org.jetbrains.kotlinx.jupyter.messaging.makeSimpleMessage
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.absolute
import kotlin.time.Duration


/**
 * Updates the metadata of the Jupyter notebook associated with the current session.
 * The metadata update involves sending a request to the kernel's `SHELL` channel
 * and waiting for the response indicating the operation's success.
 *
 * @return true if the metadata update operation completes successfully, or false otherwise
 */
suspend fun JupyterNotebookSession.updateNotebookMetadata(): Boolean {
    val notebookFilePath = virtualFile.file.toNioPath().absolute()
    val updateResult: Boolean? = sendMessageAndWait(
        channel = JupyterMessageChannel.SHELL,
        messageType = MessageType.UPDATE_CLIENT_METADATA_REQUEST,
        content = UpdateClientMetadataRequest(notebookFilePath),
        timeout = KERNEL_UPDATE_FILE_PATH_TIMEOUT
    ) { replyDeferred ->
        object : FinalizationPreservingCallback(
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
    }
    return updateResult == true
}

/**
 * Sends a message to the kernel's [channel] and waits for a reply.
 * Use [callbackFactory] to provide a callback that will be called when a reply is received.
 * Its argument, `replyDeferred`, should be completed by the client code.
 *
 * @return null if the deferred was not completed within the timeout.
 */
@OptIn(ExperimentalContracts::class)
suspend fun <T: Any> JupyterNotebookSession.sendMessageAndWait(
    channel: JupyterMessageChannel,
    messageType: MessageType,
    content: AbstractMessageContent,
    timeout: Duration,
    callbackFactory: (replyDeferred: CompletableDeferred<T>) -> JupyterExecutionCallback,
): T? {
    contract {
        callsInPlace(callbackFactory, InvocationKind.EXACTLY_ONCE)
    }

    val messageFactory = NoReplyMessageFactory(sessionId)
    val message = messageFactory.makeSimpleMessage(messageType, content)
    val zmqMessage = message.toRawMessage().toJupyterMessage(channel)

    val replyDeferred = CompletableDeferred<T>()
    val callback = callbackFactory(replyDeferred)
    sendMessageOnPooledThread(zmqMessage, callback)

    notebookLogger().info("Sending $messageType to the session with id ${sessionId}")

    val result = withTimeoutOrNull(timeout) {
        replyDeferred.await()
    }

    notebookLogger().info("Received response to $messageType to the session with id ${sessionId}: $result")

    return result
}
