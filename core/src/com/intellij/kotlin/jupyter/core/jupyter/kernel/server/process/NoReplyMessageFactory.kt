// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import kotlinx.serialization.json.JsonElement
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageContent
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory
import org.jetbrains.kotlinx.jupyter.messaging.MessageHeader
import org.jetbrains.kotlinx.jupyter.messaging.MessageType

class NoReplyMessageFactory(
    sessionId: JupyterNotebookSessionId,
    override val username: String = "username",
): MessageFactory {
    override val sessionId: String = sessionId.id
    override val contextMessage: RawMessage? get() = null
    override val messageId: List<ByteArray> = listOf(byteArrayOf(1))

    override fun updateContextMessage(contextMessage: RawMessage?) {}
    override fun updateSessionInfo(message: RawMessage) {}

    override fun makeReplyMessageOrNull(
        msgType: MessageType?,
        sessionId: String?,
        header: MessageHeader?,
        parentHeader: MessageHeader?,
        metadata: JsonElement?,
        content: MessageContent?
    ): Message? {
        return null
    }
}
