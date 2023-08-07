// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events

import com.intellij.openapi.editor.event.CaretEvent
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import kotlin.math.min


interface NotebookEventProcessor {
    fun isShouldProcess(event: NotebookHighlightingEvent): Boolean

    fun process(event: NotebookHighlightingEvent)

    fun onEventHappened(event: NotebookHighlightingEvent) {
        if (!isShouldProcess(event)) return

        process(event)
    }
}


interface NotebookDaemonFinishedEventProcessor : NotebookEventProcessor {
    override fun isShouldProcess(event: NotebookHighlightingEvent): Boolean = event is NotebookDaemonFinishedEvent
}


interface NotebookCaretMovementProcessor : NotebookEventProcessor {
    val fastMovementThreshold: Long
        get() = 500

    fun NotebookCaretMovementEvent.isFastMovement(): Boolean

    fun CaretEvent.getCellOrdinal(): NotebookCellLines.Interval {
        return editor.getCell(min(newPosition.line, editor.document.lineCount - 1))
    }

    fun processEventAdapter(event: NotebookHighlightingEvent) = Unit

    override fun process(event: NotebookHighlightingEvent) {
        processEventAdapter(event)
        if (event !is NotebookCaretMovementEvent) return

        if (event.isFastMovement()) {
            processFastCaretMovement(event)
        } else processRegularCaretMovement(event)
    }

    fun processFastCaretMovement(event: NotebookCaretMovementEvent)

    fun processRegularCaretMovement(event: NotebookCaretMovementEvent)
}
