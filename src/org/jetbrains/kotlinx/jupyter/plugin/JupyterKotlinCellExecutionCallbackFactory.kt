package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterBackedVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class JupyterKotlinCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {
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

        if (!notebookFile?.file.isKotlinNotebook) return null

        return JupyterKotlinCellExecutionCallback(
            cellProject,
            notebookFile!!,
            jupyterPsiCell,
            cellSource
        )
    }
}
