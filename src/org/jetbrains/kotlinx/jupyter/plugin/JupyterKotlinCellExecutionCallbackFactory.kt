package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager.Companion.getJupyterVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterCellExecutionCallbackFactory
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

/**
 * This factory [create] method is called on each cell execution
 * and should return the callback for the actions related to this cell.
 */
class JupyterKotlinCellExecutionCallbackFactory : JupyterCellExecutionCallbackFactory {
    override fun create(psiCell: JupyterPsiCell): JupyterExecutionCallback? {
        lateinit var notebookFile: VirtualFile // backed
        lateinit var cellProject: Project
        lateinit var jupyterPsiCell: JupyterPsiCell
        lateinit var cellSource: String
        runReadAction {
            notebookFile = psiCell.getJupyterVirtualFile()!!
            cellProject = psiCell.project
            jupyterPsiCell = psiCell
            cellSource = psiCell.text
        }

        if (!notebookFile.isKotlinNotebook) return null

        return JupyterKotlinCellExecutionCallback(
            cellProject,
            notebookFile,
            jupyterPsiCell,
            cellSource
        )
    }
}
