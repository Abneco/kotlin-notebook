// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.magics.BasicMagicHandlerFactoryProvider
import org.jetbrains.kotlinx.jupyter.magics.CompositeMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.contexts.CommandHandlingMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.CompositeMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.LibrariesMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.ReplOptionsMagicHandlerContext
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder

class IdeReplComponentsProvider(
    settings: DefaultReplSettings,
    communicationFacility: JupyterCommunicationFacility,
    commManager: CommManager,
    inMemoryHolder: InMemoryReplResultsHolder,
    private val _loggerFactory: EmbeddedKotlinKernelLoggerFactory,
) : DefaultReplComponentsProvider(
    settings,
    communicationFacility,
    commManager,
    inMemoryHolder,
) {
    override fun provideLoggerFactory(): KernelLoggerFactory {
        return _loggerFactory
    }

    override fun provideMagicsHandler(): LibrariesAwareMagicsHandler {
        val context =
            CompositeMagicHandlerContext(
                listOf(
                    CommandHandlingMagicHandlerContext(),
                    LibrariesMagicHandlerContext(librariesProcessor, libraryInfoSwitcher),
                    ReplOptionsMagicHandlerContext(replOptions),
                    EmbeddedLogLevelMagicsHandler.Context(_loggerFactory),
                ),
            )

        return CompositeMagicsHandler(context).apply {
            val factories = BasicMagicHandlerFactoryProvider().provideFactories() + EmbeddedLogLevelMagicsHandler
            createAndRegister(factories)
        }
    }
}
