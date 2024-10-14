// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.events

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.event.CaretEvent

sealed class NotebookHighlightingEvent

data object NotebookDaemonFinishedEvent : NotebookHighlightingEvent()

sealed class NotebookExecutionRelatedEvent(val isAfterSeriesOfRuns: Boolean = false) : NotebookHighlightingEvent()

class ExecutionCallbackRegistered(
    val cellOrd: Int?
) : NotebookExecutionRelatedEvent()

class ExecutionCallbackUnregistered(
    val cellOrd: Int,
    isAfterSeriesOfRuns: Boolean,
    val remainingExecutions: Collection<Int>
) : NotebookExecutionRelatedEvent(isAfterSeriesOfRuns)

data class NotebookSessionRestarted(val notebookFile: BackedNotebookVirtualFile) : NotebookExecutionRelatedEvent()


data class NotebookCaretMovementEvent(val event: CaretEvent, val cellInterval: NotebookCellLines.Interval) : NotebookHighlightingEvent() {
    val timeHappened = System.currentTimeMillis()
}

