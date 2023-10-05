// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackRegistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackUnregistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.NotebookExecutionRelatedEventsProcessor
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

class NotebookCellExecutionHighlightingHelper(
    private val project: Project,
    private val notebookFile: BackedNotebookVirtualFile
) : NotebookExecutionRelatedEventsProcessor {
    companion object {
        private val LOG = thisLogger()
        private val cellToHighlightLimit: Int = Runtime.getRuntime().availableProcessors() / 2 - 1
    }

    private val jupyterNotebookSession get() = JupyterRuntimeService.getInstance(project).getSession(notebookFile.file)
    private val dataLock = ReentrantReadWriteLock()

    private val lastExecutedIndexes = mutableSetOf<Int>()
    private val highlightingOrder = mutableSetOf<Int>()

    fun getLastExecutedCellsBatch(): Set<Int> = dataLock.read { lastExecutedIndexes.let { set ->
        set.ifEmpty { return@read mutableSetOf() }
        if (set.size < cellToHighlightLimit)
            set
        else
            set.take(cellToHighlightLimit).toSet()
      }
    }

    private fun reduceAfterExecutionTargets(completedElements: Set<Int>?): MutableSet<Int> =
        dataLock.withWriteLock {
            lastExecutedIndexes.removeIf { completedElements?.contains(it) == true }
            lastExecutedIndexes
        }

    private fun expandAfterExecutionTargets(completedElements: Set<Int>?,
                                            currentToHLQueue: MutableSet<Int>?): MutableSet<Int>? {
        val currentTargets = dataLock.withReadLock { lastExecutedIndexes }
        currentToHLQueue?.removeIf { !currentTargets.contains(it) }
        val updated = completedElements?.let {
            dataLock.withWriteLock {
                lastExecutedIndexes.addAll(completedElements)
                lastExecutedIndexes
            }
        }

        LOG.debug("Completed elements: $completedElements, after execution data: ${updated}")
        return updated
    }

    fun daemonFinished(completedElements: Set<Int>?,
                       currentToHLQueue: MutableSet<Int>?,
                       canModifyRequests: Boolean): Boolean {
        val remainingData = if (canModifyRequests && !jupyterNotebookSession.isKernelBusy()) {
            reduceAfterExecutionTargets(completedElements)
        } else expandAfterExecutionTargets(completedElements, currentToHLQueue)

        return remainingData.isNullOrEmpty()
    }


    override fun onSessionRestarted() {
        dataLock.withWriteLock {
            lastExecutedIndexes.clear()
            highlightingOrder.clear()
        }
    }

    override fun registerNewCallback(event: ExecutionCallbackRegistered) {
        event.cellOrd?.let {
            highlightingOrder.add(it)
         }
    }

    override fun unregisterCallback(event: ExecutionCallbackUnregistered) {
        val size = event.remainingExecutions.size
        if (event.isAfterSeriesOfRuns || !event.isSingleErrorRun && size < cellToHighlightLimit) {
            updateMetaStorageForHL(project, notebookFile, event.cellOrd)
        } else if (size > cellToHighlightLimit) {
            queueCurrentCell(project, notebookFile, event.cellOrd)
        }

        if (event.isSingleErrorRun && !event.remainingExecutions.contains(-1)) highlightingOrder.clear()

    }

    private fun JupyterNotebookSession?.isKernelBusy(): Boolean =
        if (this == null) false else kernelClient.executionState == JupyterExecutionState.BUSY

    private fun updateMetaStorageForHL(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        NotebookHighlightingService.getForFile(project, file)
            .onSuccessfulCellExecutionCallback(index)

        if (lastExecutedIndexes.isEmpty()) { // ensure additive operation
            lastExecutedIndexes.addAll(highlightingOrder)
        } else lastExecutedIndexes.let {
            lastExecutedIndexes.addAll(it)
        }
        highlightingOrder.clear()
        lastExecutedIndexes.addAll(highlightingOrder ?: emptyList())
    }


    private fun queueCurrentCell(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        val highlightingManager = NotebookHighlightingService.getForFile(project, file)
        // add current cell
        if (highlightingManager.completeRangeInd == index) {
            lastExecutedIndexes?.add(index)
        }
    }
}