package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterBackedVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
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
            pq.add(cnt)
            callbacksCounters[file] = (cnt + 1) to pq
            cnt
        }
    }

    // returns true if it was the last registered callback
    fun unregisterCallback(file: BackedNotebookVirtualFile, index: Int): Boolean {
        return countersLock.write {
            val (_, pq) = callbacksCounters[file] ?: return@write false
            pq.remove(index)
            pq.isEmpty()
        }
    }

    override fun create(psiCell: JupyterPsiCell): JupyterExecutionCallback? {
        var notebookFile: BackedNotebookVirtualFile? = null
        lateinit var cellProject: Project
        lateinit var jupyterPsiCell: JupyterPsiCell
        lateinit var cellSource: String
        runReadAction {
            notebookFile = psiCell.getJupyterBackedVirtualFile()!!
            cellProject = psiCell.project
            jupyterPsiCell = psiCell
            cellSource = psiCell.text
        }
        val file = notebookFile ?: return null
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
