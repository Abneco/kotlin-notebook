// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.messaging.AbstractMessageHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageRequestProcessor
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.ReplForJupyter
import java.util.concurrent.atomic.AtomicLong

class EmbeddedMessageHandler(
    private val repl: ReplForJupyter,
    private val loggerFactory: KernelLoggerFactory,
    private val commManager: CommManagerInternal,
    private val messageFactoryProvider: MessageFactoryProvider,
    private val socketManager: JupyterBaseSockets,
    private val executor: JupyterExecutor,
) : AbstractMessageHandler() {
    private val executionCount = AtomicLong(1)

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
