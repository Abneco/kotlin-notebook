// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterCellExecutionManager
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterTableOutputDataKey
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.getOutputsForIndex
import org.jetbrains.plugins.notebooks.jupyter.editor.preview.notebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterExecuteResultOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterOutputType
import org.jetbrains.plugins.notebooks.jupyter.tables.newapi.elementToString
import org.jetbrains.plugins.notebooks.jupyter.tables.newapi.hasOutputInCurrentSession
import org.jetbrains.plugins.notebooks.tables.api.DSTableText
import org.jetbrains.plugins.notebooks.tables.py.DSTableDataType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointerFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor


class KotlinDataframeOutputDataKeyExtractor : NotebookOutputDataKeyExtractor {
    override fun extract(editor: EditorImpl, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>? {
        val res = when {
            !editor.isJupyter -> null
            interval.type != NotebookCellLines.CellType.CODE -> null
            else -> extractImpl(editor, interval)
        }
        logger<KotlinDataframeOutputDataKeyExtractor>().warn("GG")
        if (res == null) {
            logger<KotlinDataframeOutputDataKeyExtractor>().warn("NULL")
        } else {
            logger<KotlinDataframeOutputDataKeyExtractor>().warn(res.map { it.toString() }.joinToString())
        }

        return if (res.isNullOrEmpty()) null else res
    }

    private fun extractImpl(editor: EditorImpl,
                            interval: NotebookCellLines.Interval): List<NotebookOutputDataKey> {
        val outputSequence = getOutputsForIndex(editor, interval.ordinal)?.outputs ?: return emptyList()


        val result = ArrayList<NotebookOutputDataKey>()
        val cellPointer = NotebookIntervalPointerFactory.get(editor).create(interval)
        for ((outputIndex, output) in outputSequence.withIndex()) {
            when (output.outputType) {
                JupyterOutputType.EXECUTE_RESULT, JupyterOutputType.DISPLAY_DATA ->
                    output.json["data"]?.asSafely<ObjectNode>()?.let { dataObject ->
                        logger<KotlinDataframeOutputDataKeyExtractor>().warn("GG")
                        val executionCount = (output as? JupyterExecuteResultOutput)?.executionCount
                        val hasOutputInCurrentSession = lazy(LazyThreadSafetyMode.NONE) {
                            editor.project?.let { project ->
                                val notebookVirtualFile = runReadAction { editor.notebook }
                                hasOutputInCurrentSession(project, notebookVirtualFile, cellPointer)
                            } ?: false
                        }
                        val kotlinDfDataKey = getKotlinDataframeOutputDataKey(
                                editor,
                                cellPointer,
                                dataObject,
                                executionCount,
                                isLastForCell = outputIndex == outputSequence.size - 1,
                                hasOutputInCurrentSession = hasOutputInCurrentSession.value)

                        if (kotlinDfDataKey != null) {
                            result.add(kotlinDfDataKey)
                        }
                    }
                else -> {}
            }
        }

        return result
    }

    private fun getKotlinDataframeOutputDataKey(editor: EditorImpl,
                                                cellPointer: NotebookIntervalPointer,
                                                dataObject: ObjectNode,
                                                executionCount: Int?,
                                                isLastForCell: Boolean,
                                                hasOutputInCurrentSession: Boolean): NotebookOutputDataKey? {
        val text: DSTableText
        val type: DSTableDataType
        when {
            isKotlinDataFrame(dataObject) -> {
                val mapper = ObjectMapper()
                val rawJson = mapper.readTree(dataObject[jsonPayloadField].asText())
                text = DSTableText(
                    //asConcatenatedRows(rawJson[serializedDataframeField]),
                    //rawJson[serializedDataframeField].asText()
                    dataObject[jsonPayloadField].asText(),
                    dataObject[jsonPayloadField].asText()
                )
                type = DSTableDataType.KT_DATAFRAME
            }
            else -> {
                return null
            }
        }
        val project = editor.project ?: return null
        val interval = cellPointer.get() ?: return null
        val notebookVirtualFile = editor.notebook
        val createNewComponent = JupyterCellExecutionManager.getInstance(project).shouldRequestNewAsyncComponent(cellPointer, notebookVirtualFile, isLastForCell)
        return JupyterTableOutputDataKey(text, executionCount, type, cellPointer, interval, createNewComponent)
    }


    private fun isKotlinDataFrame(dataObject: ObjectNode): Boolean {
        if (!dataObject.has(jsonPayloadField)) return false
        val jsonPayload = dataObject[jsonPayloadField].asText() ?: return false

        return jsonPayload.contains(serializedDataframeField)
    }

    private fun asConcatenatedRows(text: JsonNode, separator: String = ""): String {
        return if (text.isArray) {
            (text as ArrayNode).joinToString(separator = separator)
        } else {
            text.asText()
        }
    }

    companion object {
        private const val jsonPayloadField = "application/json"
        private const val serializedDataframeField = "kotlin_dataframe"
    }
}
