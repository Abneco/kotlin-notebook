// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.AbstractMessageHandler
import org.jetbrains.kotlinx.jupyter.messaging.ExecutionCounter
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageRequestProcessor
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter

class EmbeddedMessageHandler(
    private val repl: ReplForJupyter,
    private val loggerFactory: KernelLoggerFactory,
    private val commManager: CommManagerInternal,
    private val messageFactoryProvider: MessageFactoryProvider,
    private val socketManager: JupyterServerSockets,
    private val executor: JupyterExecutor,
) : AbstractMessageHandler() {
    private val executionCount = ExecutionCounter(1)

    override fun createProcessor(message: RawMessage): MessageRequestProcessor {
        return EmbeddedMessageRequestProcessor(
            message,
            messageFactoryProvider,
            socketManager,
            commManager,
            executor,
            executionCount,
            loggerFactory,
            repl,
        )
    }
}
