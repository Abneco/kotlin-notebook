package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.cellToHighlightLimit
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scheduleHLUpdate
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.removeFirstWithProvided
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
    private val lastExecutedList = mutableMapOf<BackedNotebookVirtualFile, MutableSet<Int>>()
    private val countersLock = ReentrantReadWriteLock()

    private fun registerNewCallback(file: BackedNotebookVirtualFile, cellOrd: Int?): Int {
        return countersLock.write {
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

    fun daemonFinished(file: BackedNotebookVirtualFile, project: Project, queueCompleted: Set<Int>?, document: Document? = null) = countersLock.write {
        val resulted = countersLock.withWriteLock {
            lastExecutedList[file]?.removeFirstWithProvided(cellToHighlightLimit, queueCompleted)
        }
        if (resulted?.isEmpty() == false) {
            file.file.toPsiFile(project)?.scheduleHLUpdate(document)
        }
    }

    fun getLastExecutedCellsList(file: BackedNotebookVirtualFile): Set<Int>
        = countersLock.read { lastExecutedList[file]?.let { set ->
            if (set.size < cellToHighlightLimit) set else set.take(cellToHighlightLimit).toSet()
        } ?: mutableSetOf()  }

    // returns true if it was the last registered callback and was not after single run with error
    fun unregisterCallback(file: BackedNotebookVirtualFile, index: Int, onError: Boolean = false): Boolean {
        return countersLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write false
            pq.remove(index)
            val isAfterSeriesRuns = pq.size == 1 && pq.contains(-1)
            if (isAfterSeriesRuns) pq.remove(-1)
            val singleErrorRun = onError && !isAfterSeriesRuns
            if (isAfterSeriesRuns) {
                val stateBefore = lastExecutedList[file]
                if (stateBefore.isNullOrEmpty()) { // ensure additive operation
                    lastExecutedList[file] = highlightOrder[file]?.toMutableSet() ?: mutableSetOf()
                } else highlightOrder[file]?.toMutableSet()?.let {
                    stateBefore.addAll(it)
                }
                highlightOrder[file]?.clear()
                file.file.toDocument()?.getUserData(NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange)
                    ?.compareAndSet(true, false)
            }
            if (singleErrorRun && !pq.contains(-1)) highlightOrder[file]?.clear()

            pq.isEmpty() && !singleErrorRun
        }
    }

    override fun create(task: JupyterExecutionTask): JupyterExecutionCallback? {
        val file = task.notebookVirtualFile
        val cellProject = task.project ?: return null
        val jupyterPsiCellData = runReadAction {
            val cellIndex = task.options.cellPointer?.get()?.ordinal ?: return@runReadAction null
            getCells(cellProject, task.notebookVirtualFile)?.getOrNull(cellIndex) to cellIndex
        }
        val cell = jupyterPsiCellData?.first
        if (cell == null) return null // cell is not exists already
        val cellSource = task.source
        if (!file.file.isKotlinNotebook) return null

        val index = registerNewCallback(file, jupyterPsiCellData.second)

        return JupyterKotlinCellExecutionCallback(
            cellProject,
            file,
            cell,
            cellSource,
            index,
        )
    }

    fun createNotBoundCallback(
        project: Project,
        virtualFile: BackedNotebookVirtualFile,
        source: String
    ): JupyterExecutionCallback {
        val index = registerNewCallback(virtualFile, null)
        return JupyterKotlinCellExecutionCallback(
            project,
            virtualFile,
            null,
            source,
            index,
        )
    }

    companion object {
        fun getInstance() = JupyterCellExecutionCallbackFactory.EP_NAME.findExtensionOrFail(JupyterKotlinCellExecutionCallbackFactory::class.java)
    }
}
