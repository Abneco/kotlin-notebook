package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.notebooks.core.api.psi.NotebookCell
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterAdditionalRunActionsHandler
import org.jetbrains.plugins.notebooks.jupyter.actions.getJupyterCell
import org.jetbrains.plugins.notebooks.jupyter.editor.getCells
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile

class JupyterKotlinRunActionsHandler : JupyterAdditionalRunActionsHandler {
    override fun runAll(event: AnActionEvent) {
        doHandleAllCells(event) { _, list -> list }
    }

    override fun runAllAbove(event: AnActionEvent, cell: NotebookCell) {
        doHandleAllCells(event) { data, list -> list.subList(0, list.indexOf(data.jupyterCell)) }
    }

    override fun runAllBelow(event: AnActionEvent, cell: NotebookCell) {
        doHandleAllCells(event) { data, list -> list.subList(list.indexOf(data.jupyterCell), list.size) }
    }

    override fun runCell(event: AnActionEvent, cell: NotebookCell) {
        doHandleSingleCell(event)
    }

    override fun runCellInsertBelow(event: AnActionEvent, cell: NotebookCell) {
        doHandleSingleCell(event)
    }

    override fun runCellSelectBelow(event: AnActionEvent, cell: NotebookCell) {
        doHandleSingleCell(event)
    }

    private fun doHandleSingleCell(event: AnActionEvent) {
        val cell: JupyterCell = event.dataContext.getJupyterCell() ?: return
        val project = event.dataContext.getData(PlatformDataKeys.PROJECT) ?: return

        doHandleCells(listOf(cell), project)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun doHandleCells(
        cells: List<JupyterCell>,
        project: Project,
    ) {
        // val compilerService = project.service<JupyterCompilerService>()
        // cells.forEach { compilerService.addCompiledSnippet(it.text) }
    }

    private fun doHandleAllCells(
        event: AnActionEvent,
        cellsFilter: (JupyterExecutionData, List<JupyterCell>) -> List<JupyterCell>
    ) {
        val executionData = getJupyterExecutionData(event) ?: return
        val project = executionData.project
        val jupyterVirtualFile = executionData.notebookVirtualFile

        getCells(project, jupyterVirtualFile)?.let {
            val filteredList = cellsFilter(executionData, it)
            doHandleCells(filteredList, project)
        }
    }
}

data class JupyterExecutionData(
    val project: Project,
    val jupyterCell: JupyterCell,
    val notebookVirtualFile: NotebookVirtualFile
)

fun JupyterCell.getVirtualFile(): NotebookVirtualFile? = containingFile.virtualFile as? NotebookVirtualFile

fun getJupyterExecutionData(event: AnActionEvent): JupyterExecutionData? {
    val psiCell = event.getJupyterCell() ?: return null
    val project = event.dataContext.getData(PlatformDataKeys.PROJECT) ?: return null
    val jupyterVirtualFile = psiCell.getVirtualFile() ?: return null
    return JupyterExecutionData(project, psiCell, jupyterVirtualFile)
}

fun getCells(project: Project, notebookVirtualFile: NotebookVirtualFile?): List<JupyterCell>? =
    notebookVirtualFile?.let { getCells(PsiManager.getInstance(project).findFile(it) as JupyterFile) }
