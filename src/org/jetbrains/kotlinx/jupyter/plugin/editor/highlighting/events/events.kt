// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events

import com.intellij.openapi.editor.event.CaretEvent
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import com.intellij.notebooks.visualization.NotebookCellLines

sealed class NotebookHighlightingEvent

data object NotebookDaemonFinishedEvent : NotebookHighlightingEvent()

sealed class NotebookExecutionRelatedEvent(val isAfterSeriesOfRuns: Boolean = false) : NotebookHighlightingEvent()

class ExecutionCallbackRegistered(
    val cellOrd: Int?
) : NotebookExecutionRelatedEvent()

class ExecutionCallbackUnregistered(
    val cellOrd: Int,
    isAfterSeriesOfRuns: Boolean,
    val isSingleErrorRun: Boolean,
    val remainingExecutions: Collection<Int>
) : NotebookExecutionRelatedEvent(isAfterSeriesOfRuns)

data class NotebookSessionRestarted(val notebookFile: BackedNotebookVirtualFile) : NotebookExecutionRelatedEvent()


data class NotebookCaretMovementEvent(val event: CaretEvent, val cellInterval: NotebookCellLines.Interval) : NotebookHighlightingEvent() {
    val timeHappened = System.currentTimeMillis()
}

