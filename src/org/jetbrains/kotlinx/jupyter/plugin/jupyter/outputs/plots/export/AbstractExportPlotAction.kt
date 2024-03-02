// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotComponent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.PlotDataKeyExtractor
import org.jetbrains.kotlinx.jupyter.plugin.util.firstAncestorOfType
import org.jetbrains.plugins.notebooks.core.api.getNotebookCellAndFile
import org.jetbrains.plugins.notebooks.core.impl.actions.NotebookEditorActionBase
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterEditorActionsUtils.getNotebookFile
import org.jetbrains.plugins.notebooks.jupyter.editor.getCellIndex
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookObjectOutputDataKeyExtractor
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterDisplayDataOutput


abstract class AbstractExportPlotAction : NotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val letsPlotOutputs = getLetsPlotOutputs(event)
            .takeIf { isActionApplicable(it) } ?: return

        val notebookFile = event.getNotebookFile() ?: return
        doExport(letsPlotOutputs, project, notebookFile)
    }

    protected abstract fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: BackedNotebookVirtualFile,
    )

    protected open fun isActionApplicable(outputs: Collection<LetsPlotOutputDataKey>): Boolean {
        return outputs.isNotEmpty()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        super.update(event)
        val letsPlotOutputs = getLetsPlotOutputs(event)
        doUpdate(event, letsPlotOutputs)
    }

    protected open fun doUpdate(event: AnActionEvent, letsPlotOutputs: List<LetsPlotOutputDataKey>) {
        event.presentation.isEnabledAndVisible = isActionApplicable(letsPlotOutputs)
    }

    private fun getLetsPlotOutputs(event: AnActionEvent): List<LetsPlotOutputDataKey> {
        val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val letsPlotComponent = contextComponent?.firstAncestorOfType<LetsPlotComponent>()
        if (letsPlotComponent != null) {
            return listOfNotNull(letsPlotComponent.dataKey)
        }

        val (psiCell, notebookVirtualFile) = event.dataContext.getNotebookCellAndFile() ?: return emptyList()
        return getLetsPlotOutputs(notebookVirtualFile, psiCell.getCellIndex())
    }

    private fun getLetsPlotOutputs(notebookVirtualFile: BackedNotebookVirtualFile, cellIndex: Int): List<LetsPlotOutputDataKey> {
        val notebook = notebookVirtualFile.notebook
        val jupyterCell = notebook.cells[cellIndex]

        val outputs = jupyterCell.outputs ?: return emptyList()

        val extractor = NotebookObjectOutputDataKeyExtractor.EP_NAME.findExtension(PlotDataKeyExtractor::class.java) ?: return emptyList()

        return outputs.outputs.filterIsInstanceAnd<JupyterDisplayDataOutput> { output ->
            output.data.has(PlotDataKeyExtractor.PLOT_KEY)
        }.mapNotNull { letPlotOutput ->
            extractor.extractKey(letPlotOutput.data.toV4Json(), null)
        }
    }

}
