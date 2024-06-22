// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.utils.ifEmpty
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackRegistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.ExecutionCallbackUnregistered
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.events.NotebookExecutionRelatedEventsProcessor
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterExecutionState
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.notebook.JupyterRuntimeService
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

class NotebookCellExecutionHighlightingHelper(
    private val project: Project,
    private val notebookFile: BackedNotebookVirtualFile
) : NotebookExecutionRelatedEventsProcessor {
    companion object {
        private val LOG = thisLogger()
        private val cellToHighlightLimit: Int = Runtime.getRuntime().availableProcessors() / 2 - 1

        private enum class ExecutionState {
            PENDING_REQUEST,
            IDLE
        }
    }

    init {
        val parentDisposable = NotebookHighlightingService.getInstance(project)
        project.messageBus.connect(parentDisposable).subscribe(
            NotebookSessionEventListener.TOPIC,
            object : NotebookSessionEventListener {
                override fun sessionStarted(virtualFile: BackedNotebookVirtualFile, isAfterRestart: Boolean) {
                    dataLock.withWriteLock {
                        lastExecutedIndexes.clear()
                    }
                }
            }
        )
    }

    private val jupyterNotebookSession get() = JupyterRuntimeService.getInstance(project).getSession(notebookFile)
    private val dataLock = ReentrantReadWriteLock()
    private val executionState = AtomicReference(ExecutionState.IDLE)

    private val lastExecutedIndexes = mutableSetOf<Int>()

    fun getLastExecutedCellsBatch(): Set<Int> = dataLock.read { lastExecutedIndexes.let { indexes ->
        indexes.ifEmpty { return@read mutableSetOf() }
        if (indexes.size < cellToHighlightLimit)
            indexes
        else
            indexes.take(cellToHighlightLimit).toSet()
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

    private fun isCanModifyRequestData(scriptingStateFlag: Boolean): Boolean =
        when {
            jupyterNotebookSession == null -> {
                executionState.set(ExecutionState.IDLE)
                true
            }
            else -> scriptingStateFlag &&
                    !jupyterNotebookSession.isKernelBusy() &&
                    executionState.get() != ExecutionState.PENDING_REQUEST
        }

    fun daemonFinished(completedElements: Set<Int>?,
                       currentToHLQueue: MutableSet<Int>?,
                       canModifyRequests: Boolean): Boolean {
        val remainingData = if (isCanModifyRequestData(canModifyRequests)) {
            reduceAfterExecutionTargets(completedElements)
        } else expandAfterExecutionTargets(completedElements, currentToHLQueue)
        remainingData?.let { currentToHLQueue?.addAll(it) }

        return remainingData.isNullOrEmpty()
    }


    override fun onSessionRestarted() {
        dataLock.withWriteLock {
            lastExecutedIndexes.clear()
        }
    }

    override fun registerNewCallback(event: ExecutionCallbackRegistered) {
        event.cellOrd?.let {
            if (executionState.compareAndSet(ExecutionState.IDLE, ExecutionState.PENDING_REQUEST)) {
                LOG.info("Set execution state to PENDING_REQUEST")
            }
            lastExecutedIndexes.add(it)
         }
    }

    override fun unregisterCallback(event: ExecutionCallbackUnregistered) {
        fun ifKernelDoneProcessingRequests() =
            !jupyterNotebookSession.isKernelBusy() && executionState.compareAndSet(ExecutionState.PENDING_REQUEST, ExecutionState.IDLE)

        val size = event.remainingExecutions.size
        /*if (event.isAfterSeriesOfRuns || !event.isSingleErrorRun && size < cellToHighlightLimit) {
            updateMetaStorageForHL(project, notebookFile, event.cellOrd)
        } else*/
        if (size > 0 && (!event.isAfterSeriesOfRuns || size > cellToHighlightLimit)) {
            // LOG.warn("Unregister callback, but size is: $size")
            queueCurrentCell(project, notebookFile, event.cellOrd)
        }
        if (size == 0 && ifKernelDoneProcessingRequests()) {
            LOG.info("Set execution state to IDLE")
        }
    }

    private fun JupyterNotebookSession?.isKernelBusy(): Boolean =
        if (this == null) false else kernelClient.executionState == JupyterExecutionState.BUSY

    @Deprecated("Unused logic, to be removed")
    private fun updateMetaStorageForHL(project: Project, file: BackedNotebookVirtualFile, index: Int) = Unit


    private fun queueCurrentCell(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        val highlightingManager = NotebookHighlightingService.getForFile(project, file)
        // add current cell
        if (highlightingManager.completeRangeInd == index) {
            lastExecutedIndexes.add(index)
        }
    }
}