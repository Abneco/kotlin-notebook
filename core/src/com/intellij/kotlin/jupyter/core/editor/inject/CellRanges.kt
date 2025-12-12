// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.inject

import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookExtraLanguage
import com.intellij.openapi.util.TextRange

/**
 * Ranges of code fragments and magics of the cell
 * Code ranges are grouped by language
 * It's guaranteed that:
 * - There are no overlapping ranges
 * - Each position within the cell is covered by exactly one range
 * - In each of the lists ranges are sorted in ascending order
 * - There is at least one code range (maybe empty)
 */
data class CellRanges(
    // Null key means default language (Kotlin)
    val codeRanges: Map<NotebookExtraLanguage?, List<TextRange>>,
    val magicRanges: List<TextRange>,
)
