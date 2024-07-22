// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCounter
import org.jetbrains.kotlinx.jupyter.messaging.IdeCompatibleMessageRequestProcessor
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

class EmbeddedMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    socketManager: JupyterBaseSockets,
    commManager: CommManagerInternal,
    executor: JupyterExecutor,
    executionCount: ExecutionCounter,
    loggerFactory: KernelLoggerFactory,
    repl: ReplForJupyter,
): IdeCompatibleMessageRequestProcessor(
    rawIncomingMessage,
    messageFactoryProvider,
    socketManager,
    commManager,
    executor,
    executionCount,
    loggerFactory,
    repl
){
    override fun processInputReply(content: InputReply) {
        socketManager.stdin.setClientReply(incomingMessage.toRawMessage())
    }
}
