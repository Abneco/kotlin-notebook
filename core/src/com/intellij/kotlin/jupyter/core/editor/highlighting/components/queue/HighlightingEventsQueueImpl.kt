// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.HighlightingComponent
import com.intellij.kotlin.jupyter.core.editor.highlighting.restarter.NotebookHighlightingRestarter
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

internal class HighlightingEventsQueueImpl(
    private val project: Project,
    private val notebookVirtualFile: BackedNotebookVirtualFile,
) : HighlightingEventsQueue, HighlightingComponent() {
    companion object {
        private val LOG = notebookLogger()
    }

    private val eventsQueue: ConcurrentLinkedQueue<HighlightingEvent> = ConcurrentLinkedQueue()
    private val psiFileRef by lazy {
        withReadAccess {
            AtomicReference<PsiFile?>(
                notebookVirtualFile.file.findPsiFile(project)
            )
        }
    }

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
            changedCells.addIfNotNull(prevFocusCell)
        }
        changedCells.add(focusCell)

        return HighlightingEvent(
            focusCell = focusCell,
            previousFocusCell = prevFocusCell,
            changedCells = changedCells,
            false
        )
    }

    override fun pushEvent(event: HighlightingEvent) {
        eventsQueue.add(event)

        if (event.isCustomEditorEvent) {
            requestHLRestart()
        }
    }

    override fun pullEvents(): HighlightingEvent? {
        val events = mutableListOf<HighlightingEvent>()

        // Drain all events atomically
        try {
            while (true) {
                val event = eventsQueue.poll() ?: break
                events.add(event)
            }
            ProgressManager.checkCanceled()

            return mergeEvents(events)
        } catch (t: ProcessCanceledException) {
            eventsQueue.addAll(events)
            throw t
        }
    }

    override fun clear() {
        eventsQueue.clear()
    }

    private fun requestHLRestart() {
        val psi = psiFileRef.get() ?: run {
            val foundPsi = notebookVirtualFile.file.findPsiFile(project)
            psiFileRef.set(foundPsi)
            foundPsi
        }
        
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