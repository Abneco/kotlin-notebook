package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.editor.getCells
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionTask
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class JupyterKotlinCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {

    private val callbacksCounters = mutableMapOf<BackedNotebookVirtualFile, Pair<Int, PriorityQueue<Int>>>()
    private val countersLock = ReentrantReadWriteLock()

    private fun registerNewCallback(file: BackedNotebookVirtualFile): Int {
        return countersLock.write {
            val (cnt, pq) = callbacksCounters[file] ?: (0 to PriorityQueue<Int>())
            if (pq.size > 1 && !pq.contains(-1)) {
                pq.add(-1)
            }
            pq.add(cnt)
            callbacksCounters[file] = (cnt + 1) to pq
            cnt
        }
    }

    // returns true if it was the last registered callback and was not after single run with error
    fun unregisterCallback(file: BackedNotebookVirtualFile, index: Int, onError: Boolean = false): Boolean {
        return countersLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write false
            pq.remove(index)
            val isAfterSeriesRuns = pq.size == 1 && pq.contains(-1)
            if (isAfterSeriesRuns) pq.remove(-1)
            val singleErrorRun = onError && !isAfterSeriesRuns
            pq.isEmpty() && !singleErrorRun
        }
    }

    override fun create(task: JupyterExecutionTask): JupyterExecutionCallback? {
        val file = task.notebookVirtualFile
        val cellProject = task.project ?: return null
        val jupyterPsiCell = runReadAction {
            val cellIndex = task.options.cellPointer?.get()?.ordinal ?: return@runReadAction null
            getCells(cellProject, task.notebookVirtualFile)?.getOrNull(cellIndex)
        }
        if (jupyterPsiCell == null) return null // cell is not exists already
        val cellSource = task.source
        if (!file.file.isKotlinNotebook) return null

        val index = registerNewCallback(file)

        return JupyterKotlinCellExecutionCallback(
            cellProject,
            file,
            jupyterPsiCell,
            cellSource,
            index,
        )
    }

    companion object {
        fun getInstance() = JupyterCellExecutionCallbackFactory.EP_NAME.findExtensionOrFail(JupyterKotlinCellExecutionCallbackFactory::class.java)
    }
}
