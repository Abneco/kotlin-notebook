// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.util.data

import com.intellij.jupyter.core.core.impl.actions.NotebookCellsContents.Companion.CELL_HEADER
import com.intellij.kotlin.jupyter.test.util.kotlinCell
import org.jetbrains.jupyter.builder.NotebookBuilder


/**
 * Populates a notebook with cells from textual representation based on the [CELL_HEADER].
 * Adds cells to the [NotebookBuilder].
 *
 * @see TEMPLATE_DATA_EXTENSION
 */
internal fun NotebookBuilder.parseFromRawInput(input: String) {
    val cellParts = input.splitByCellSeparator()
    if (cellParts.isEmpty()) {
        error("No cell data found in $input")
    }

    for (cellText in cellParts) {
        val lines = cellText.lines()
        if (lines.isEmpty()) continue

        val header = lines.first().trim()
        val body = lines.drop(1).joinToString("\n").trimEnd()

        when {
            header.contains("md") -> markdownCell(body)
            header.trim() == CELL_HEADER -> kotlinCell(body)
            else -> {
                error("Unknown cell header: $header")
            }
        }
    }
}

/**
 * Splits the receiver string into blocks, each starting with a `#%%` separator line.
 */
private fun String.splitByCellSeparator(): List<String> {
    return trimIndent()
        .split(CELL_SEPARATOR_REGEX)
        .map { it.trimEnd('\n', '\r') }
        .filter { it.isNotBlank() }
}