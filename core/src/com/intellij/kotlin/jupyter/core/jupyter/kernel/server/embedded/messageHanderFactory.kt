// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.defaultSystemProperties
import com.intellij.kotlin.jupyter.core.util.getOrSetSystemProperty
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlinx.jupyter.api.embedded.InMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutor
import org.jetbrains.kotlinx.jupyter.execution.JupyterExecutorImpl
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacilityImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProvider
import org.jetbrains.kotlinx.jupyter.messaging.MessageFactoryProviderImpl
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.messaging.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplFactory
import org.jetbrains.kotlinx.jupyter.repl.creating.loadDefaultReplFactory
import kotlin.io.path.pathString

fun createEmbeddedMessageHandler(
    project: Project,
    replSettings: DefaultReplSettings,
    loggerFactory: EmbeddedKotlinKernelLoggerFactory,
    socketManager: JupyterServerSockets,
    inMemoryHolder: InMemoryReplResultsHolder,
    kernelVersion: String,
): MessageHandler {
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)
    val executor: JupyterExecutor = JupyterExecutorImpl(loggerFactory)
    val commManager: CommManagerInternal = CommManagerImpl(communicationFacility)
    val replComponentsProvider = IdeReplComponentsProvider(replSettings, communicationFacility, commManager, inMemoryHolder, loggerFactory)
    val replFactory = getReplFactory(project, kernelVersion, replComponentsProvider)
    val repl = replFactory.createRepl()
    return EmbeddedMessageHandler(repl, loggerFactory, commManager, messageFactoryProvider, socketManager, executor)
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
    updateHomePathProperty()
    setDefaultSystemProperties()
    val classLoader = EmbeddedKernelClassLoaderHolder.getInstance(project).getClassLoader(kernelVersion)
    return loadDefaultReplFactory(replComponentsProvider, classLoader)
}

/**
 * Kotlin compiler that is embedded into our kernel and is used for snippet compilation
 * has its own version of some intellij classes, including [PathManager]. These classes are relocated so that
 * `com.intellij.openapi.application.PathManager` becomes `ktnb.org.jetbrains.kotlin.com.intellij.openapi.application.PathManager`.
 * They are generally fairly outdated, so the platform changes might not be reflected in these classes
 *
 * Here, we set a system property if it was not set to bypass the faulty logic of home path detection
 * inside this relocated class. In the case the property is set, the path will be taken from there, and that's it.
 */
private fun updateHomePathProperty() {
    getOrSetSystemProperty(PathManager.PROPERTY_HOME_PATH) {
        PathManager.getHomeDir(true).pathString
    }
}

/**
 * Sets some system properties to the default values if they are not set yet.
 */
private fun setDefaultSystemProperties() {
    for ((key, value) in defaultSystemProperties) {
        getOrSetSystemProperty(key) { value }
    }
}
