// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.magics.IdeCompatibleMagicsHandler
import org.jetbrains.kotlinx.jupyter.magics.LibrariesAwareMagicsHandler
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.repl.creating.DefaultReplComponentsProvider

class IdeReplComponentsProvider(
    settings: DefaultReplSettings,
    communicationFacility: JupyterCommunicationFacility,
    commManager: CommManager,
) : DefaultReplComponentsProvider(
    settings,
    communicationFacility,
    commManager
) {
    override fun provideMagicsHandler(): LibrariesAwareMagicsHandler {
        return IdeCompatibleMagicsHandler(replOptions, librariesProcessor, libraryInfoSwitcher)
    }
}
