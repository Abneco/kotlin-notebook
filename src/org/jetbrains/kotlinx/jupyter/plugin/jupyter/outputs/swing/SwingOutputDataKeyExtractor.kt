// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.InMemoryMimeTypes
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded.InMemoryReplResultsHolderService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookObjectOutputDataKeyExtractor

/**
 * Extract Swing in-memory data to display in a [SwingComponent] (if applicable).
 */
class SwingOutputDataKeyExtractor : NotebookObjectOutputDataKeyExtractor {
    override fun extractKey(
        project: Project?,
        file: BackedNotebookVirtualFile?,
        dataObject: ObjectNode,
        executionCount: Int?
    ): SwingOutputDataKey? {
        if (project == null) return null
        if (!dataObject.has(InMemoryMimeTypes.SWING)) return null
        val node = dataObject[InMemoryMimeTypes.SWING] as? TextNode ?: return null
        val id = node.textValue()
        val runtimeService = JupyterRuntimeService.getInstance(project)
        val sessionId = file?.let { runtimeService.getSession(it)?.sessionId } ?: return null
        val inMemoryHolder = InMemoryReplResultsHolderService.getInstance(project).getHolder(sessionId) ?: return null
        return inMemoryHolder.getReplResult(id)?.let {
            SwingOutputDataKey(it, executionCount)
        }
    }
}
