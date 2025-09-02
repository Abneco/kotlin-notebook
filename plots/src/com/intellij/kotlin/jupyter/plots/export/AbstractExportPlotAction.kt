// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterDisplayDataOutput
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.jupyter.core.jupyter.ui.traverseChildrenBreadthFirst
import com.intellij.kotlin.jupyter.core.util.filterIsInstanceAnd
import com.intellij.kotlin.jupyter.core.util.firstAncestorOfType
import com.intellij.kotlin.jupyter.plots.LetsPlotComponent
import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey
import com.intellij.kotlin.jupyter.plots.PlotDataKeyExtractor
import com.intellij.notebooks.visualization.context.NotebookDataContext.selectedCellInterval
import com.intellij.notebooks.visualization.outputs.impl.InnerComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class AbstractExportPlotAction : NotebookEditorActionBase() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val letsPlotOutputs = getLetsPlotOutputs(event)
            .takeIf { isActionApplicable(it) } ?: return

        val notebookFile = event.notebookFile ?: return
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

    protected open fun supportsMultiplePlots(): Boolean = false

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) {
            val letsPlotOutputs = getLetsPlotOutputs(event)
            doUpdate(event, letsPlotOutputs)
        }
    }

    protected open fun doUpdate(event: AnActionEvent, letsPlotOutputs: List<LetsPlotOutputDataKey>) {
        event.presentation.isEnabledAndVisible = isActionApplicable(letsPlotOutputs)
    }

    private fun getLetsPlotOutputs(event: AnActionEvent): List<LetsPlotOutputDataKey> {
        val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        if (supportsMultiplePlots()) {
            val innerComponent = contextComponent?.firstAncestorOfType<InnerComponent>()
            if (innerComponent != null) {
                val result = innerComponent
                    .traverseChildrenBreadthFirst()
                    .asSequence()
                    .filterIsInstance<LetsPlotComponent>()
                    .mapNotNull { it.dataKey }
                    .toList()
                    .takeIf { it.isNotEmpty() }
                if (result != null) return result
            }
        } else {
            val letsPlotComponent = contextComponent?.firstAncestorOfType<LetsPlotComponent>()
            if (letsPlotComponent != null) {
                return listOfNotNull(letsPlotComponent.dataKey)
            }
        }

        val notebookVirtualFile = event.notebookFile ?: return emptyList()
        val hoveredInterval = event.dataContext.selectedCellInterval ?: return emptyList()

        return getLetsPlotOutputs(notebookVirtualFile, hoveredInterval.ordinal)
    }

    private fun getLetsPlotOutputs(notebookVirtualFile: BackedNotebookVirtualFile, cellIndex: Int): List<LetsPlotOutputDataKey> {
        val notebook = notebookVirtualFile.notebook
        val jupyterCell = notebook.getCell(cellIndex)

        val outputs = jupyterCell.outputs ?: return emptyList()

        val extractor = NotebookDisplayOutputDataKeyExtractor.EP_NAME.findExtension(PlotDataKeyExtractor::class.java) ?: return emptyList()

        return outputs.outputs.filterIsInstanceAnd<JupyterDisplayDataOutput> { output ->
            output.data.has(MimeType.LETS_PLOT.mimeType)
        }.mapNotNull { letPlotOutput ->
            extractor.extractKey(letPlotOutput.data, null)
        }
    }
}