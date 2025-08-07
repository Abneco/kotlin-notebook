// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Disposes all the given [highlighters].
 */
internal fun disposeOfHighlighters(highlighters: Collection<RangeHighlighter>) {
    if (highlighters.isEmpty()) return
    KotlinNotebookPluginScope.invokeOnEDT {
        for (highlighter in highlighters) {
            highlighter.dispose()
        }
    }
}