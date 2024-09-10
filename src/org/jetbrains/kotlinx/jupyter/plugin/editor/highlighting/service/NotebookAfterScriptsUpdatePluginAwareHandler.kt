// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener


/**
 * An internal functional interface aimed assist HL substystem to process events, after which scripts are indeed updated.
 * Designed to handle scripts update events while being aware of the current Kotlin plugin mode.
 *
 * @see NotebookHighlightingManager and it's [addListeners]
 */
internal fun interface NotebookAfterScriptsUpdatePluginAwareHandler : KotlinPluginModeAwareHandler, NotebookCodeSnippetsChangeListener