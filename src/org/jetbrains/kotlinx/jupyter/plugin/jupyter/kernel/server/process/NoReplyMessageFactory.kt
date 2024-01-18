// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId

class NoReplyMessageFactory(
    sessionId: JupyterNotebookSessionId,
    override val username: String = "username",
): MessageFactory {
    override val sessionId: String = sessionId.id
    override val contextMessage: RawMessage? get() = null
    override val messageId: List<ByteArray> = listOf(byteArrayOf(1))

    override fun updateContextMessage(contextMessage: RawMessage?) {}
    override fun updateSessionInfo(message: RawMessage) {}
}
