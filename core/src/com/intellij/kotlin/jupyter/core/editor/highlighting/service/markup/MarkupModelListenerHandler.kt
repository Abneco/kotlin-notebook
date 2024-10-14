// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.markup

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Provides instances of `MarkupModelListener` based on the current mode of the Kotlin plugin.
 * This is a part of introducing our own highlighting style for errors in cells
 * which are not inside the cell of the focus, aka "Shadowing" errors.
 *
 * Listener is necessary to keep track of error highlighters which are effectively applied to the model.
 */
object MarkupModelListenerPluginAwareProvider : KotlinPluginModeAwareHandler {
    fun provideListener(highlighters: MutableSet<RangeHighlighter>): MarkupModelListener {
        return createPluginModeAwareInstance(
            highlighters,
            ::createK1Listener,
            ::createK2Listener,
        )
    }

    private fun createK1Listener(highlighters: MutableSet<RangeHighlighter>): MarkupModelListener {
        return ShadowingAwareMarkupModelListener(highlighters)
    }

    private fun createK2Listener(highlighters: MutableSet<RangeHighlighter>) : MarkupModelListener {
        // shadowing is not yet supported
        return object : MarkupModelListener { }
    }
}