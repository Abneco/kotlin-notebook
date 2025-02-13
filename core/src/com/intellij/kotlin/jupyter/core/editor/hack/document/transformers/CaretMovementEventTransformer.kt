// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.document.transformers

import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.Editor
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
    private var lastCellIndex: Int = -1
    private var focusCellIndex: Int = -1
    private val stateLock = ReentrantReadWriteLock()

    override fun transformRawInput(rawEvent: CaretEvent): HighlightingEvent {
        val cell = editor.getCell(min(rawEvent.newPosition.line, editor.document.lineCount - 1))
        val ord = cell.ordinal
        val (newFocusIndex, previousFocus) = stateLock.write {
            lastCellIndex = focusCellIndex
            focusCellIndex = ord

            ord to lastCellIndex
        }

        return HighlightingEvent(
            newFocusIndex,
            if (previousFocus == ord || previousFocus == -1) null else previousFocus,
            null
        )
    }
}