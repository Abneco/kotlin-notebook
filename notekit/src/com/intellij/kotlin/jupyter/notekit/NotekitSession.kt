// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.notekit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.core.impl.actions.NotebookCellLinesDocumentUtils.insertCells
import com.intellij.jupyter.core.core.impl.actions.NotebookCellLinesDocumentUtils.removeCells
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jackson

import com.intellij.jupyter.core.jupyter.nbformat.JupyterCell
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.notekit.i18n.NotekitBundle
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.notebooks.psi.jupyter.nbformat.JupyterCellType

/**
 * Handles protocol requests for a single comm session.
 */
class NotekitSession(
    private val project: Project,
    private val notebookFile: BackedNotebookVirtualFile,
) {
    private val notebook: JupyterNotebook?
        get() = notebookFile.notebookOrNull

    private val document: Document?
        get() = FileDocumentManager.getInstance().getDocument(notebookFile.file)

    private val methods = mapOf<String, suspend (NotekitRequest) -> ObjectNode>(
        "get_cell_count" to ::getCellCount,
        "get_notebook_metadata" to ::getNotebookMetadata,
        "get_cell_range" to ::getCellRange,
        "splice_cell_range" to ::spliceCellRange,
        "set_notebook_metadata" to ::setNotebookMetadata,
        "execute_cell_range" to ::executeCellRange,
        "get_nbformat_version" to ::getNbformatVersion,
    )

    suspend fun processRequest(request: NotekitRequest): ObjectNode {
        return try {
            val handler = methods[request.method]
                          ?: return request.error(NotekitProtocol.ERROR_UNKNOWN_METHOD, "Unknown method: ${request.method}")
            handler(request)
        }
        catch (e: Exception) {
            LOG.error("Error processing notekit request: ${request.method}", e)
            request.error(NotekitProtocol.ERROR_INTERNAL_ERROR, e.message ?: "Internal error")
        }
    }

    private suspend fun getCellCount(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val count = readAction { nb.cellsCount() }
        return request.success(jackson.createObjectNode().put("count", count))
    }

    private suspend fun getNotebookMetadata(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val metadata = readAction { (nb.json["metadata"] as? ObjectNode)?.deepCopy() ?: jackson.createObjectNode() }
        return request.success(jackson.createObjectNode().set("metadata", metadata))
    }

    private suspend fun getCellRange(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val range = request.cellRange() ?: return request.missingRangeError()

        val cellCount = readAction { nb.cellsCount() }
        validateRange(range, cellCount)?.let { return request.error(it) }

        val cellsArray = readAction {
            jackson.createArrayNode().apply {
                for (i in range) {
                    add(nb.getCell(i).toJson())
                }
            }
        }
        return request.success(jackson.createObjectNode().set("cells", cellsArray))
    }

    private suspend fun spliceCellRange(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val doc = readAction { document } ?: return request.noNotebookError()

        val start = request.intParam("start") ?: return request.error(
            NotekitProtocol.ERROR_INVALID_SPLICE_PARAMS, "Missing 'start' parameter"
        )
        val deleteCount = request.intParam("delete_count", 0)
        val cellsToInsert = request.cellsParam() ?: jackson.createArrayNode()

        val newCells = cellsToInsert.mapNotNull { CellData.fromJson(it) }
        if (newCells.size != cellsToInsert.size()) {
            return request.error(NotekitProtocol.ERROR_INVALID_CELL_DATA, "Invalid cell data in cells array")
        }

        var affectedEnd = start
        var spliceError: ObjectNode? = null

        executeWriteCommand("command.notekit.splice.cells") {
            val cellLines = NotebookCellLines.get(doc)
            val intervals = cellLines.intervals

            // Validate against actual intervals count
            validateSpliceRange(start, deleteCount, intervals.size)?.let { (code, msg) ->
                spliceError = request.error(code, msg)
                return@executeWriteCommand
            }

            val ptrFactory = NotebookIntervalPointerFactory.get(project, doc)

            if (deleteCount > 0) {
                doc.removeCells(intervals.subList(start, start + deleteCount).toList(), ptrFactory)
            }

            if (newCells.isNotEmpty()) {
                val insertedIntervals = doc.insertCells(cellLines, newCells.toCellText(), start)
                affectedEnd = start + insertedIntervals.size

                // Apply metadata to inserted cells
                newCells.forEachIndexed { index, cellData ->
                    val cellIndex = start + index
                    if (cellIndex < nb.cellsCount()) {
                        val cell = nb.getCell(cellIndex)
                        for ((key, value) in cellData.metadata.properties()) {
                            cell.setMetadata(key, value)
                        }
                    }
                }
            }
        }

        return spliceError ?: request.success(jackson.createObjectNode().apply {
            set<ObjectNode>("affected_range", jackson.createObjectNode().put("start", start).put("end", affectedEnd))
        })
    }

    private suspend fun setNotebookMetadata(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val metadata = request.nodeParam("metadata")
                       ?: return request.error(NotekitProtocol.ERROR_INVALID_METADATA, "Missing 'metadata' parameter")
        val merge = request.boolParam("merge", true)

        executeWriteCommand("command.notekit.set.metadata") {
            if (!merge) {
                val existing = nb.json["metadata"] as? ObjectNode
                existing?.removeAll()
            }
            for ((key, value) in metadata.properties()) {
                nb.setMetadata(key, value)
            }
        }
        return request.success()
    }

    private suspend fun executeCellRange(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val range = request.cellRange() ?: return request.missingRangeError()

        val cellCount = readAction { nb.cellsCount() }
        validateRange(range, cellCount)?.let { return request.error(it) }

        try {
            val intervalPointers = readAction {
                val doc = document ?: return@readAction emptyList()
                val factory = NotebookIntervalPointerFactory.get(project, doc)
                val intervals = NotebookCellLines.get(doc).intervals
                range.mapNotNull { index ->
                    intervals.getOrNull(index)?.let { factory.create(it) }
                }
            }

            if (intervalPointers.isNotEmpty()) {
                JupyterExecutionManager
                    .getInstanceOrCreate(project, notebookFile)
                    .runCells(intervalPointers)
                    .awaitAll()
            }
        }
        catch (e: Exception) {
            LOG.error("Cell execution failed", e)
            return request.error(NotekitProtocol.ERROR_EXECUTION_FAILED, "Cell execution failed: ${e.message}")
        }
        return request.success()
    }

    private suspend fun getNbformatVersion(request: NotekitRequest): ObjectNode {
        val nb = notebook ?: return request.noNotebookError()
        val (nbformat, nbformatMinor) = readAction {
            val nbformat = nb.json["nbformat"]?.asInt() ?: 4
            val nbformatMinor = nb.json["nbformat_minor"]?.asInt() ?: 0
            nbformat to nbformatMinor
        }
        return request.success(jackson.createObjectNode().apply {
            put("nbformat", nbformat)
            put("nbformat_minor", nbformatMinor)
        })
    }

    private data class NotekitError(val code: String, val message: String)

    private fun NotekitRequest.cellRange(): IntRange? {
        val end = intParam("end") ?: return null
        return intParam("start", 0) until end
    }

    private fun NotekitRequest.noNotebookError() =
        error(NotekitProtocol.ERROR_NO_ACTIVE_NOTEBOOK, "No active notebook is available")

    private fun NotekitRequest.missingRangeError() =
        error(NotekitProtocol.ERROR_INVALID_RANGE, "Missing 'end' parameter")

    private fun NotekitRequest.error(err: NotekitError) = error(err.code, err.message)

    private fun validateRange(range: IntRange, cellCount: Int): NotekitError? = when {
        range.first < 0 || range.isEmpty() -> NotekitError(
            NotekitProtocol.ERROR_INVALID_RANGE,
            "Invalid cell range: start=${range.first}, end=${range.last + 1}"
        )
        range.last >= cellCount -> NotekitError(
            NotekitProtocol.ERROR_OUT_OF_BOUNDS,
            "Cell range out of bounds: end=${range.last + 1} exceeds cell count of $cellCount"
        )
        else -> null
    }

    private fun validateSpliceRange(start: Int, deleteCount: Int, intervalsCount: Int): NotekitError? = when {
        start > intervalsCount -> NotekitError(
            NotekitProtocol.ERROR_INVALID_SPLICE_PARAMS,
            "Invalid splice parameters: start=$start exceeds intervals count of $intervalsCount"
        )
        start + deleteCount > intervalsCount -> NotekitError(
            NotekitProtocol.ERROR_INVALID_SPLICE_PARAMS,
            "Invalid splice parameters: start=$start + delete_count=$deleteCount exceeds intervals count of $intervalsCount"
        )
        else -> null
    }

    private fun JupyterCell.toJson(): ObjectNode =
        jackson.createObjectNode().apply {
            put("cell_type", cellType.name.lowercase())
            put("source", source)
            set<JsonNode>("metadata", getAllMetadataDeepCopy())
            if (cellType == JupyterCellType.CODE) {
                executionCount?.let { put("execution_count", it) }
                outputs?.json?.let { set<ArrayNode>("outputs", it) }
            }
        }

    private fun List<CellData>.toCellText(): String = joinToString("\n") { cell ->
        val cellType = when (cell.cellType) {
            "markdown" -> CellType.MARKDOWN
            "raw" -> CellType.RAW
            else -> CellType.CODE
        }
        "${cellType.cellHeader}\n${cell.source}"
    }

    private suspend fun executeWriteCommand(commandKey: String, action: () -> Unit) {
        withContext(Dispatchers.EDT) {
            WriteCommandAction.runWriteCommandAction(
                project,
                NotekitBundle.message("command.notekit.group"),
                NotekitBundle.message(commandKey),
                Runnable(action)
            )
        }
    }

    companion object {
        private val LOG = logger<NotekitSession>()
    }
}
