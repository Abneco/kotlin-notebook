// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass.state

import com.intellij.openapi.editor.markup.RangeHighlighter

/**
 * Represents the status of Notebook Highlighting pass progress.
 * This information is used to dispose of error highlighters and to feed left indexes to the next pass.
 *
 * @see [com.intellij.kotlin.jupyter.core.editor.hack.NotebookPassConfiguration]
 */
data class NotebookPassProgressStatus(
    val leftIndexesToProcess: Set<Int>,
    val errorHighlightersOutsideOfFocus: Collection<RangeHighlighter>
)