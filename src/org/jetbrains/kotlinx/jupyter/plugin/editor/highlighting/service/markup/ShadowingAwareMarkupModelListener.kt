// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.markup

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Base implementation for keeping track of error highlighters from K1 errors factory.
 * Might be reused for K2.
 */
internal class ShadowingAwareMarkupModelListener(private val errorHighlighters: MutableSet<RangeHighlighter>) : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) {
        val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return
        // ignore parsing errors for now, only from KT factories
        if (info.severity == HighlightSeverity.ERROR && info.description.startsWith('[')) {
            errorHighlighters.add(highlighter)
        }
    }
}