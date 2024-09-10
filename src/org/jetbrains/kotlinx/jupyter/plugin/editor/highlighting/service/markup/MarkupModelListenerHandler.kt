// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.markup

import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.createPluginModeAwareInstance

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
        return object : MarkupModelListener { }
    }
}