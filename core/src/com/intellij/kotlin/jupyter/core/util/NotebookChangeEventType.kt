// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

/**
 * Specifies which type of change has happened to the notebook document.
 */
enum class NotebookChangeEventType {
    CELL_ADD,
    CELL_DELETE,
    MARKDOWN_CONVERSION,
    REGULAR
}

/**
 * Indicates which direction the cell was moved in the notebook document.
 */
enum class NotebookMoveEvent {
    CELL_UP,
    CELL_DOWN
}