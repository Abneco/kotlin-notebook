// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.cells

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.helper.editor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.jupyter.core.jupyter.nbformat.getCellByPointer
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.notebooks.visualization.context.NotebookDataContext.selectedCellInterval
import com.intellij.openapi.actionSystem.DataContext


internal inline fun <reified T : StorableCellMetadata> BackedNotebookVirtualFile.readCellMetadata(
    cellPointer: NotebookIntervalPointer,
    dataKey: StorableCellMetadataKey<T>
): T? = notebook.getCellByPointer(cellPointer)?.readCellMetadata(dataKey)


internal fun <T : StorableCellMetadata> BackedNotebookVirtualFile.writeCellMetadata(
    cellPointer: NotebookIntervalPointer,
    key: StorableCellMetadataKey<T>,
    value: T
) {
    val cell = notebook.getCellByPointer(cellPointer)
    if (cell == null) {
        return
    }

    cell.writeCellMetadata(key, value)
}

/**
 * NB: invoking this method on project disposal is not guaranteed to access a valid [JupyterNotebook]
 */
internal fun <T: StorableCellMetadata> BackedNotebookVirtualFile.clearAllCellsDataByKey(dataKey: StorableCellMetadataKey<T>) {
    notebook.clearAllCellsDataByKey(dataKey)
}

internal fun <T: StorableCellMetadata> JupyterNotebook.clearAllCellsDataByKey(dataKey: StorableCellMetadataKey<T>) {
    for (cell in computeCells()) {
        cell.removeMetadata(dataKey.metadataKey)
    }
}

private fun DataContext.notebookFileAndCellPointer(): Pair<BackedNotebookVirtualFile, NotebookIntervalPointer>? {
    val editor = editor ?: return null
    val interval = selectedCellInterval ?: return null
    val intervalPointer = interval.let {
        val factory = NotebookIntervalPointerFactory.get(editor)
        factory.create(it)
    }
    return notebookFile?.to(intervalPointer)
}