// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.markup

import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter

internal class MarkUpModelListener(val errorHighlighters: MutableSet<RangeHighlighter>) : MarkupModelListener {
    override fun afterAdded(highlighter: RangeHighlighterEx) {
        if (highlighter.layer == HighlighterLayer.ERROR) {
            errorHighlighters.add(highlighter)
        }
    }
}