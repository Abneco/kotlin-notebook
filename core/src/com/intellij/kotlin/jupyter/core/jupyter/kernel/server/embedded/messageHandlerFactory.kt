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
import org.jetbrains.kotlinx.jupyter.messaging.comms.server.ServerCommCommunicationFacility
import org.jetbrains.kotlinx.jupyter.protocol.JupyterServerSockets
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommManagerImpl
import org.jetbrains.kotlinx.jupyter.protocol.comms.CommManagerInternal
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.creating.ReplFactory
import org.jetbrains.kotlinx.jupyter.repl.creating.loadDefaultReplFactory
import kotlin.io.path.pathString

@RequiresBackgroundThread
fun createEmbeddedMessageHandler(
    project: Project,
    replSettings: DefaultReplSettings,
    loggerFactory: EmbeddedKotlinKernelLoggerFactory,
    socketManager: JupyterServerSockets,
    inMemoryHolder: InMemoryReplResultsHolder,
    kernelVersion: String,
): EmbeddedMessageHandler {
    val messageFactoryProvider: MessageFactoryProvider = MessageFactoryProviderImpl()
    val communicationFacility: JupyterCommunicationFacility = JupyterCommunicationFacilityImpl(socketManager, messageFactoryProvider)
    val executor: JupyterExecutor = JupyterExecutorImpl(loggerFactory)
    val commCommunicationFacility = ServerCommCommunicationFacility(communicationFacility)
    val commManager: CommManagerInternal = CommManagerImpl(commCommunicationFacility)
    val classLoader = getEmbeddedKernelClassLoader(project, kernelVersion)
    val replComponentsProvider = IdeReplComponentsProvider(
        settings = replSettings,
        communicationFacility = communicationFacility,
        commManager = commManager,
        inMemoryHolder = inMemoryHolder,
        _loggerFactory = loggerFactory,
        compilerServiceSpiClassloader = classLoader,
    )
    val replFactory = getReplFactory(kernelVersion, replComponentsProvider, classLoader)
    val repl = replFactory.createRepl()
    return EmbeddedMessageHandler(repl, loggerFactory, commManager, messageFactoryProvider, socketManager, executor)
}

/**
 * Loads kernel artifact for a specific [kernelVersion],
 * creates [java.net.URLClassLoader], loads kernel classes from it and instantiates
 * [ReplFactory] via SPI with the components provided by [replComponentsProvider].
 */
@RequiresBackgroundThread
private fun getReplFactory(
    kernelVersion: String,
    replComponentsProvider: ReplComponentsProvider,
    embeddedKernelClassLoader: ClassLoader,
): ReplFactory {
    return loadDefaultReplFactory(replComponentsProvider, embeddedKernelClassLoader)
}

@RequiresBackgroundThread
private fun getEmbeddedKernelClassLoader(project: Project, kernelVersion: String): ClassLoader {
    ThreadingAssertions.assertBackgroundThread()
    updateHomePathProperty()
    ensureSystemPropertiesSet(defaultSystemProperties)
    return EmbeddedKernelClassLoaderHolder.getInstance(project).getClassLoader(kernelVersion)
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
 * Sets system properties to the default values provided in [systemProperties], if they are not set yet.
 */
private fun ensureSystemPropertiesSet(systemProperties: Map<String, String>) {
    for ((key, value) in systemProperties) {
        getOrSetSystemProperty(key) { value }
    }
}
