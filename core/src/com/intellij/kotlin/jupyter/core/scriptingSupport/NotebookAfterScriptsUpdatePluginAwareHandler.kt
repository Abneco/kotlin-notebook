// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener


/**
 * A functional interface aimed to process events, after which scripts are indeed updated from Model perspective.
 * Designed to handle scripts update events while being aware of the current Kotlin plugin mode.
 *
 * @see NotebookHighlightingManager and it's [addListeners]
 */
fun interface NotebookAfterScriptsUpdatePluginAwareHandler : KotlinPluginModeAwareHandler, NotebookScriptsStateListener