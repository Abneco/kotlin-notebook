// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.queue

/**
 * Representation of a transformed input event
 * containing information about HL pass targets.
 *
 * @see [com.intellij.kotlin.jupyter.core.editor.highlighting.pass.NotebookPassConfiguration]
 */
internal data class HighlightingEvent(
    val focusCell: Int,
    val previousFocusCell: Int?,
    val changedCells: Collection<Int>?,
    // is used to understand if custom highlighting restart is needed
    val isCustomEditorEvent: Boolean = false,
)