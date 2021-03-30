package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class JupyterKotlinCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {
    private var instance: JupyterExecutionCallback? = null

    override fun create(psiCell: JupyterPsiCell): JupyterExecutionCallback {
        instance?.let { return it }

        lateinit var notebookFile: NotebookVirtualFile
        lateinit var cellProject: Project
        runReadAction {
            notebookFile = psiCell.getJupyterVirtualFile()!!
            cellProject = psiCell.project
        }

        val callback = JupyterKotlinCellExecutionCallback(
            cellProject,
            notebookFile
        )
        instance = callback
        return callback
    }
}
