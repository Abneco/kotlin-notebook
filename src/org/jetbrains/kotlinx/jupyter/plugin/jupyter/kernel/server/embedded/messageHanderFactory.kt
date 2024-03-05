// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterBaseSockets
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplFactory
import org.jetbrains.kotlinx.jupyter.repl.creating.loadDefaultReplFactory

fun createEmbeddedMessageHandler(
    project: Project,
    replSettings: DefaultReplSettings,
    socketManager: JupyterBaseSockets,
): MessageHandler {
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)
    val executor: JupyterExecutor = JupyterExecutorImpl()
    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val replComponentsProvider = DefaultReplComponentsProvider(replSettings, communicationFacility, commManager)
    val kernelVersion = KotlinNotebookProjectOptionsProvider.getInstance(project).kernelVersion
    val replFactory = getReplFactory(project, kernelVersion, replComponentsProvider)
    val repl = replFactory.createRepl()
    return EmbeddedMessageHandler(repl, commManager, messageFactoryProvider, socketManager, executor)
}

/**
 * Loads kernel artifact for a specific [kernelVersion],
 * creates [java.net.URLClassLoader], loads kernel classes from it and instantiates
 * [ReplFactory] via SPI with the components provided by [replComponentsProvider].
 */
@RequiresBackgroundThread
fun getReplFactory(
    project: Project,
    kernelVersion: String,
    replComponentsProvider: ReplComponentsProvider,
): ReplFactory {
    ThreadingAssertions.assertBackgroundThread()
    val classLoader = EmbeddedKernelClassLoaderHolder.getInstance(project).getClassLoader(kernelVersion)
    return loadDefaultReplFactory(replComponentsProvider, classLoader)
}
