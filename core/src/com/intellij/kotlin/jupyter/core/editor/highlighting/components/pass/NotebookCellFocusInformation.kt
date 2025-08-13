// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass

import com.intellij.openapi.util.TextRange

/**
 * Represents information about the current cell in focus, including its [TextRange] and index.
 */
data class NotebookCellFocusInformation(
    val focusCellIndex: Int,
    val cellRange: TextRange,
)