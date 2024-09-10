// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener


/**
 * An internal functional interface aimed to process events, after which scripts are indeed updated from Model perspectivr.
 * Designed to handle scripts update events while being aware of the current Kotlin plugin mode.
 *
 * @see NotebookHighlightingManager and it's [addListeners]
 */
internal fun interface NotebookAfterScriptsUpdatePluginAwareHandler : KotlinPluginModeAwareHandler, NotebookCodeSnippetsChangeListener