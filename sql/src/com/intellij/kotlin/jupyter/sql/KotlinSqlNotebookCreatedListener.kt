// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.database.psi.DbPsiFacade
import com.intellij.dataspell.jupyter.sql.common.metadata.sqlMetadata
import com.intellij.dataspell.jupyter.sql.common.metadata.sqlMetadataOrDefault
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.NotebookEditorCreatedCallback
import com.intellij.jupyter.core.jupyter.helper.isJupyter
import com.intellij.jupyter.core.jupyter.nbformat.CellAdded
import com.intellij.jupyter.core.jupyter.nbformat.JupyterCell
import com.intellij.jupyter.core.jupyter.nbformat.JupyterCellCountListener
import com.intellij.jupyter.core.jupyter.nbformat.notifyNotebookChanged
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.plugins.notebooks.psi.jupyter.nbformat.JupyterCellType

/**
 * Listens for the creation of Kotlin notebooks editors to add another listener to the created
 * editor that adds pre-filling of data sources for SQL cells.
 */
class KotlinSqlNotebookCreatedListener : NotebookEditorCreatedCallback {
    override fun editorCreated(editor: Editor) {
        if (!editor.isJupyter) return
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (!file.isKotlinNotebook) return

        val notebookFile = BackedNotebookVirtualFile.takeIfBacked(file) ?: return
        val jupyterChangeListener = JupyterCellCountListener { event ->
            if (event is CellAdded && event.cell.cellType == JupyterCellType.SQL) {
                handleSqlCellAdded(notebookFile, event.cell, editor)
            }
        }
        notebookFile.notebook.listeners.cellCountListeners.addListener(jupyterChangeListener)
    }

    private fun handleSqlCellAdded(notebookFile: BackedNotebookVirtualFile, cell: JupyterCell, editor: Editor) {
        val sources = editor.project?.let { DbPsiFacade.getInstance(it).dataSources }?.map { it.name }
                      ?: return
        if (sources.isEmpty()) return

        val usedDataSource: String?
        if (sources.size == 1) {
            // There is only one data source, so we can use it by default
            usedDataSource = sources.first()
        }
        else {
            // Check if we can pre-select the last used data source
            val previouslyUsedDataSource = getNameOfPreviouslyUsedDataSource(notebookFile, cell.index)
            if (previouslyUsedDataSource == null) {
                // There is no previously used datasource, so we can use the first one provided by the database plugin
                usedDataSource = sources.first()
            }
            else {
                // We can use the previously used datasource (or the first one if the data source is not known in this project)
                usedDataSource = sources.firstOrNull { it == previouslyUsedDataSource } ?: sources.first()
            }
        }
        cell.sqlMetadata = cell.sqlMetadataOrDefault.copy(dataSourceName = usedDataSource)
        notebookFile.notebook.notifyNotebookChanged()
    }

    private fun getNameOfPreviouslyUsedDataSource(notebookFile: BackedNotebookVirtualFile, currentIndex: Int?): String? {
        if (currentIndex == null) {
            // we can't guarantee that the found datasource is actually used by the cell prior to the new cell
            return null
        }

        return notebookFile.notebook.computeCells()
            .filter { cell -> cell.cellType == JupyterCellType.SQL && (cell.index != null && cell.index!! < currentIndex) }
            .sortedBy { cell -> cell.index }
            .reversed()
            .firstNotNullOfOrNull { cell -> cell.sqlMetadata?.dataSourceName }
    }

}
