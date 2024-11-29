// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.markup

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
        /*
         Hack: ignore parsing errors for now, only from KT factories, as [HighlightInfo]
         does not contain info about from which factory it was created.They are in the form of
         [Factory]: <error>
         */
        if (info.severity == HighlightSeverity.ERROR && info.description.startsWith('[')) {
            errorHighlighters.add(highlighter)
        }
    }
}