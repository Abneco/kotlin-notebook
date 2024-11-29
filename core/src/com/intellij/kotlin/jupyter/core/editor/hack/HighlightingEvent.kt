// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack


/**
 * Representation of a transformed input event
 * containing information about HL pass targets.
 *
 * @see [com.intellij.kotlin.jupyter.core.editor.hack.service.NotebookPassConfiguration]
 */
internal data class HighlightingEvent(
    val focusCell: Int,
    val previousFocusCell: Int?,
    val changedCells: Collection<Int>?
)