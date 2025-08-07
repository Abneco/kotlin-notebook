// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass.state

import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Represents state after the Notebook Highlighting pass completed.
 * This information is used to dispose of error highlighters and to set up new pass.
 *
 * @see [com.intellij.kotlin.jupyter.core.editor.hack.NotebookPassConfiguration]
 */
data class NotebookPassProgressRemains(
    val leftIndexesToProcess: Collection<Int>,
    val errorHighlightersOutsideOfFocus: Collection<RangeHighlighter>
)