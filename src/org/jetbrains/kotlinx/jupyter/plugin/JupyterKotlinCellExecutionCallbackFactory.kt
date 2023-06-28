package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.cellToHighlightLimit
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.editor.getCells
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class JupyterKotlinCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {

    private val callbacksCounters = mutableMapOf<BackedNotebookVirtualFile, Pair<Int, PriorityQueue<Int>>>()
    private val highlightOrder = mutableMapOf<BackedNotebookVirtualFile, MutableSet<Int>>()
    private val lastExecutedIndexes = mutableMapOf<BackedNotebookVirtualFile, MutableSet<Int>>()
    private val executionDataLock = ReentrantReadWriteLock()

    private fun registerNewCallback(file: BackedNotebookVirtualFile, cellOrd: Int?): Int {
        return executionDataLock.write {
            val (cnt, pq) = callbacksCounters[file] ?: (0 to PriorityQueue<Int>())
            if (pq.size > 1 && !pq.contains(-1)) {
                pq.add(-1)
            }
            pq.add(cnt)

            if (cellOrd != null) {
                val order = highlightOrder.getOrPut(file) { mutableSetOf() }
                order.add(cellOrd)
            }

            callbacksCounters[file] = (cnt + 1) to pq
            cnt
        }
    }

    fun resetPreviousData(file: BackedNotebookVirtualFile) {
        executionDataLock.withWriteLock {
            lastExecutedIndexes[file]?.clear()
            highlightOrder[file]?.clear()
            callbacksCounters.remove(file)
        }
    }

    // true if it has no updates left
    fun daemonFinished(file: BackedNotebookVirtualFile,
                       completedElements: Set<Int>?,
                       currentToHLQueue: MutableSet<Int>?,
                       canModifyRequests: Boolean): Boolean {
        val remainingData = if (canModifyRequests) {
            executionDataLock.withWriteLock {
                lastExecutedIndexes[file]?.removeIf { completedElements?.contains(it) == true }
                lastExecutedIndexes[file]
            }
        } else executionDataLock.withReadLock { lastExecutedIndexes[file] }
            .let { execRequests ->
                currentToHLQueue?.removeIf { execRequests?.contains(it) == false }
                val updated = completedElements?.let { executionDataLock.withWriteLock {
                    lastExecutedIndexes[file]?.addAll(completedElements)
                    lastExecutedIndexes[file]}
                }
                LOG.debug("Completed elements: $completedElements, after execution data: ${updated}")
                updated
            }

        return remainingData.isNullOrEmpty()
    }

    fun getLastExecutedCellsBatch(file: BackedNotebookVirtualFile): Set<Int>
        = executionDataLock.read { lastExecutedIndexes[file]?.let { set ->
            if (set.size < cellToHighlightLimit) set else set.take(cellToHighlightLimit).toSet()
        } ?: mutableSetOf()  }

    // returns true if it was the last registered callback and was not after single run with error
    fun unregisterCallback(project: Project, file: BackedNotebookVirtualFile, index: Int, onError: Boolean = false): Boolean {
        return executionDataLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write false
            pq.remove(index)
            val isAfterSeriesRuns = pq.size == 1 && pq.contains(-1)
            if (isAfterSeriesRuns) pq.remove(-1)
            val singleErrorRun = onError && !isAfterSeriesRuns
            if (isAfterSeriesRuns || !singleErrorRun && pq.size < cellToHighlightLimit) {
                updateMetaStorageForHL(project, file, index)
            } else if (pq.size > cellToHighlightLimit) {
                queueCellHLIfUnderCaret(project, file, index)
            }

            if (singleErrorRun && !pq.contains(-1)) highlightOrder[file]?.clear()

            pq.isEmpty() && !singleErrorRun
        }
    }

    private fun updateMetaStorageForHL(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        NotebookHighlightingService.getForFile(project, file)
            .onSuccessfulCellExecutionCallback(index)

        if (lastExecutedIndexes[file].isNullOrEmpty()) { // ensure additive operation
            lastExecutedIndexes[file] = highlightOrder[file]?.toMutableSet() ?: mutableSetOf()
        } else highlightOrder[file]?.toMutableSet()?.let {
            lastExecutedIndexes[file]?.addAll(it)
        }
        highlightOrder[file]?.clear()
        lastExecutedIndexes[file]?.addAll(highlightOrder[file] ?: emptyList())
    }

    private fun queueCellHLIfUnderCaret(project: Project, file: BackedNotebookVirtualFile, index: Int) {
        val highlightingManager = NotebookHighlightingService.getForFile(project, file)
        // add current cell
        if (highlightingManager.completeRangeInd == index) {
            if (lastExecutedIndexes[file].isNullOrEmpty())
                lastExecutedIndexes[file] = mutableSetOf(index)
            else lastExecutedIndexes[file]?.add(index)
        }
    }

    override fun create(task: JupyterExecutionTask): JupyterExecutionCallback? {
        val file = task.notebookVirtualFile
        val cellProject = task.project ?: return null
        val jupyterPsiCellData = runReadAction {
            val cellIndex = task.options.cellPointer?.get()?.ordinal ?: return@runReadAction null
            getCells(cellProject, task.notebookVirtualFile)?.getOrNull(cellIndex) to cellIndex
        }
        val cell = jupyterPsiCellData?.first ?: return null
        if (!file.file.isKotlinNotebook) return null

        val index = registerNewCallback(file, jupyterPsiCellData.second)

        return JupyterKotlinCellExecutionCallback(
            cellProject,
            file,
            cell,
            index,
        )
    }

    fun createNotBoundCallback(
        project: Project,
        virtualFile: BackedNotebookVirtualFile
    ): JupyterExecutionCallback {
        val index = registerNewCallback(virtualFile, null)
        return JupyterKotlinCellExecutionCallback(
            project,
            virtualFile,
            null,
            index,
        )
    }

    companion object {
        private val LOG = logger<JupyterKotlinCellExecutionCallbackFactory>()
        fun getInstance() = JupyterCellExecutionCallbackFactory.EP_NAME.findExtensionOrFail(JupyterKotlinCellExecutionCallbackFactory::class.java)
    }
}
