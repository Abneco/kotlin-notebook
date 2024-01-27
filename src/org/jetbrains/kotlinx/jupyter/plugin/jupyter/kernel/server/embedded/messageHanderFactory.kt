// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings

fun createInProcessMessageHandler(
    replSettings: DefaultReplSettings,
    socketManager: JupyterBaseSockets,
): MessageHandler {
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)

    val executor: JupyterExecutor = JupyterExecutorImpl()

    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val repl = EmbeddedReplFactory(replSettings, communicationFacility, commManager).createRepl()
    return EmbeddedMessageHandler(repl, commManager, messageFactoryProvider, socketManager, executor)
}
