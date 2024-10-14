// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.events

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.event.CaretEvent
import kotlin.math.min


interface NotebookEventProcessor {
    fun shouldProcess(event: NotebookHighlightingEvent): Boolean

    fun process(event: NotebookHighlightingEvent)

    fun processEventAdapter(event: NotebookHighlightingEvent) = Unit

    fun onEventHappened(event: NotebookHighlightingEvent) {
        processEventAdapter(event)
        if (!shouldProcess(event)) return

        process(event)
    }
}


interface NotebookDaemonFinishedEventProcessor : NotebookEventProcessor {
    override fun shouldProcess(event: NotebookHighlightingEvent): Boolean = event is NotebookDaemonFinishedEvent
}


interface NotebookCaretMovementProcessor : NotebookEventProcessor {
    val fastMovementThreshold: Long
        get() = 500

    fun NotebookCaretMovementEvent.isFastMovement(): Boolean

    fun CaretEvent.getCellOrdinal(): NotebookCellLines.Interval {
        return editor.getCell(min(newPosition.line, editor.document.lineCount - 1))
    }

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



interface NotebookExecutionRelatedEventsProcessor : NotebookEventProcessor {
    override fun shouldProcess(event: NotebookHighlightingEvent): Boolean =
        event is NotebookExecutionRelatedEvent

    override fun process(event: NotebookHighlightingEvent) {
        when (event) {
            is ExecutionCallbackRegistered -> { registerNewCallback(event) }
            is ExecutionCallbackUnregistered -> { unregisterCallback(event) }
            is NotebookSessionRestarted -> { onSessionRestarted() }
            else -> {}
        }
    }

    fun onSessionRestarted()
    fun registerNewCallback(event: ExecutionCallbackRegistered)

    fun unregisterCallback(event: ExecutionCallbackUnregistered)

}
