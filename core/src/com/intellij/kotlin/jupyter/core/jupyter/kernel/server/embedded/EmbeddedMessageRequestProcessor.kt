// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCounter
import org.jetbrains.kotlinx.jupyter.messaging.IdeCompatibleMessageRequestProcessor
import org.jetbrains.kotlinx.jupyter.messaging.InputReply
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.toRawMessage
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

class EmbeddedMessageRequestProcessor(
    rawIncomingMessage: RawMessage,
    messageFactoryProvider: MessageFactoryProvider,
    socketManager: JupyterServerSockets,
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
