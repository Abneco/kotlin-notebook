// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.LetsPlotComponent
import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey
import com.intellij.kotlin.jupyter.plots.PlotDataKeyExtractor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.LETS_PLOT_MIME
import org.jetbrains.kotlinx.jupyter.plugin.util.filterIsInstanceAnd
import org.jetbrains.kotlinx.jupyter.plugin.util.firstAncestorOfType
import com.intellij.jupyter.core.core.api.getNotebookCellAndFile
import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.core.impl.file.notebook
import com.intellij.jupyter.core.jupyter.editor.getCellIndex
import com.intellij.jupyter.core.jupyter.editor.getJupyterVirtualFile
import com.intellij.jupyter.core.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.nbformat.JupyterDisplayDataOutput


abstract class AbstractExportPlotAction : NotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val letsPlotOutputs = getLetsPlotOutputs(event)
            .takeIf { isActionApplicable(it) } ?: return

        val notebookFile = event.getJupyterVirtualFile() ?: return
        doExport(letsPlotOutputs, project, notebookFile.file)
    }

    protected abstract fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: VirtualFile,
    )

    protected open fun isActionApplicable(outputs: List<LetsPlotOutputDataKey>): Boolean {
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
        val jupyterCell = notebook.computeCells()[cellIndex]

        val outputs = jupyterCell.outputs ?: return emptyList()

        val extractor = NotebookDisplayOutputDataKeyExtractor.EP_NAME.findExtension(PlotDataKeyExtractor::class.java) ?: return emptyList()

        return outputs.outputs.filterIsInstanceAnd<JupyterDisplayDataOutput> { output ->
            output.data.has(LETS_PLOT_MIME)
        }.mapNotNull { letPlotOutput ->
            extractor.extractKey(letPlotOutput.data, null)
        }
    }

}
