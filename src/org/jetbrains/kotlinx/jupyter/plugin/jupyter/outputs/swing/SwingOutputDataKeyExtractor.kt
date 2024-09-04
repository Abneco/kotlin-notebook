// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing

import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.InMemoryMimeTypes
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded.InMemoryReplResultsHolderService
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.jupyter.core.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.nbformat.DisplayDataContainer
import com.intellij.notebooks.visualization.NotebookIntervalPointer

/**
 * Extract Swing in-memory data to display in a [SwingComponent] (if applicable).
 */
class SwingOutputDataKeyExtractor : NotebookDisplayOutputDataKeyExtractor {
    fun extractKey(
      project: Project?,
      file: BackedNotebookVirtualFile?,
      data: DisplayDataContainer,
      executionCount: Int?,
    ): SwingOutputDataKey? {
        if (project == null) return null
        val dataObject = data.toV4Json()
        if (!dataObject.has(InMemoryMimeTypes.SWING)) return null
        val node = dataObject[InMemoryMimeTypes.SWING] as? TextNode ?: return null
        val id = node.textValue()
        val runtimeService = JupyterRuntimeService.getInstance(project)
        val sessionId = file?.let { runtimeService.getNotebookSession(it)?.sessionId } ?: return null
        val inMemoryHolder = InMemoryReplResultsHolderService.getInstance(project).getHolder(sessionId) ?: return null
        return inMemoryHolder.getReplResult(id)?.let {
            SwingOutputDataKey(it, executionCount)
        }
    }

    override fun extractKey(
      editor: EditorImpl,
      file: BackedNotebookVirtualFile?,
      data: DisplayDataContainer,
      executionCount: Int?,
      cellPointer: NotebookIntervalPointer,
      isLastForCell: Boolean
    ): SwingOutputDataKey? {
        return extractKey(editor.project, file, data, executionCount)
    }
}
