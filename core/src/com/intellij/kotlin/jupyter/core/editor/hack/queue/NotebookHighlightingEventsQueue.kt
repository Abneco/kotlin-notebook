// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.queue

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingRestarter
import com.intellij.kotlin.jupyter.core.util.toPsiFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentLinkedQueue

internal class NotebookHighlightingEventsQueue(
    private val project: Project,
    private val notebookVirtualFile: BackedNotebookVirtualFile,
    parentDisposable: Disposable,
) : HighlightingEventsQueue, Disposable {
    companion object {
        private val LOG = thisLogger()
    }

    private val eventsQueue: MutableCollection<HighlightingEvent> = ConcurrentLinkedQueue()
    private var psiFile: PsiFile? = notebookVirtualFile.file.toPsiFile(project)

    private fun mergeEvents(events: List<HighlightingEvent>) : HighlightingEvent? {
        if (events.isEmpty()) {
            return null
        }

        var focusCell: Int = events.first().focusCell
        var prevFocusCell: Int? = null
        val changedCells = mutableSetOf<Int>()

        for (event in events) {
            val eventCells = event.changedCells
            if (eventCells != null) {
                changedCells.addAll(eventCells)
            }
            focusCell = event.focusCell
            prevFocusCell = event.previousFocusCell
        }

        return HighlightingEvent(
            focusCell = focusCell,
            previousFocusCell = prevFocusCell,
            changedCells = changedCells
        )
    }

    override fun pushEvent(event: HighlightingEvent) {
        eventsQueue.add(event)

        requestHLRestart()
    }

    override fun pullEvents(): HighlightingEvent {
        val events = eventsQueue.toList()
        val mergedEvent = mergeEvents(events)
        if (mergedEvent == null) {
            return HighlightingEvent(0, null, emptySet())
        }

        eventsQueue.removeAll(events)
        return mergedEvent
    }

    override fun clear() {
        eventsQueue.clear()
        psiFile = null
    }

    private fun requestHLRestart() {
        val psi = psiFile ?: notebookVirtualFile.file.toPsiFile(project)
        if (psi == null) {
            LOG.error("Cannot find psi file for notebook file: ${notebookVirtualFile.file}")
            return
        }

        NotebookHighlightingRestarter.scheduleRegularUpdate(psi)
    }

    override fun dispose() {
        clear()
    }
}