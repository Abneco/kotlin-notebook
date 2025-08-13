// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.document.transformers

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.math.min


/**
 * Class is responsible for processing input caret events.
 * Result of transformation is a particular [HighlightingEvent] to be passed to the queue.
 *
 * Should be registered as child [HighlightingComponent].
 */
internal class CaretMovementEventTransformer(private val editor: Editor) : RawInputEventTransformer<CaretEvent>, HighlightingComponent() {
    private val stateLock = ReentrantReadWriteLock()

    override fun transformRawInput(rawEvent: CaretEvent): HighlightingEvent? {
        val newCell = rawEvent.newPosition.getCell()
        val oldCell = rawEvent.oldPosition.getCell()
        val ord = newCell.ordinal
        if (ord == oldCell.ordinal) return null

        val (newFocusCellIndex, previousFocusCellIndex) = stateLock.write {
            ord to rawEvent.oldPosition.getCell().ordinal
        }

        return HighlightingEvent(
            newFocusCellIndex,
            if (previousFocusCellIndex == ord || previousFocusCellIndex == -1) null else previousFocusCellIndex,
            null,
            isCustomEditorEvent = true
        )
    }

    private fun LogicalPosition.getCell(): NotebookCellLines.Interval {
        return editor.getCell(min(line, editor.document.lineCount - 1))
    }
}