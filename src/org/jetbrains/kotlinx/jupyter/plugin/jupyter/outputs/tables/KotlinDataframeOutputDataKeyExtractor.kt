// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.lang.Language
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.editor.language
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterTableOutputDataKey
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.getOutputsForIndex
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterExecuteResultOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterOutputType
import org.jetbrains.plugins.notebooks.jupyter.tables.newapi.createTableOutputDataKey
import org.jetbrains.plugins.notebooks.tables.api.DSTableText
import org.jetbrains.plugins.notebooks.tables.py.DSTableDataType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointerFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor


/**
 * Extract [JupyterTableOutputDataKey] from Kotlin Dataframe produced cell output
 */
class KotlinDataframeOutputDataKeyExtractor : NotebookOutputDataKeyExtractor {
    override fun extract(editor: EditorImpl, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>? {
        val res = when {
            !isSwingUiEnabledForKotlinDataframe -> null
            !editor.isJupyter -> null
            editor.isJupyter && editor.language?.isKindOf(Language.findLanguageByID("Python")) == true -> null
            interval.type != NotebookCellLines.CellType.CODE -> null
            else -> extractImpl(editor, interval)
        }

        return if (res.isNullOrEmpty()) null else res
    }

    private fun extractImpl(editor: EditorImpl,
                            interval: NotebookCellLines.Interval): List<NotebookOutputDataKey> {
        val outputSequence = getOutputsForIndex(editor, interval.ordinal)?.outputs ?: return emptyList()
        val cellPointer = NotebookIntervalPointerFactory.get(editor).create(interval)

        val result = ArrayList<NotebookOutputDataKey>()
        for ((outputIndex, output) in outputSequence.withIndex()) {
            if (output.outputType != JupyterOutputType.EXECUTE_RESULT &&
                output.outputType != JupyterOutputType.DISPLAY_DATA) {
                 continue
            }
            output.json["data"]?.asSafely<ObjectNode>()?.let { dataObject ->
                val executionCount = (output as? JupyterExecuteResultOutput)?.executionCount

                val kotlinDfDataKey = getKotlinDataframeOutputDataKey(
                    editor,
                    cellPointer,
                    dataObject,
                    executionCount,
                    isLastForCell = outputIndex == outputSequence.size - 1
                )

                if (kotlinDfDataKey != null) {
                    result.add(kotlinDfDataKey)
                }
            }
        }

        return result
    }

    private fun getKotlinDataframeOutputDataKey(editor: EditorImpl,
                                                cellPointer: NotebookIntervalPointer,
                                                dataObject: ObjectNode,
                                                executionCount: Int?,
                                                isLastForCell: Boolean): NotebookOutputDataKey? {
        if (!KotlinDataframeParsing.isKotlinDataFrame(dataObject)) {
            return null
        }

        val serializedDataframe = dataObject.toString()

        val text = DSTableText(truncatedTableText = serializedDataframe, plainText = serializedDataframe)
        val type = DSTableDataType.EXTERNAL

        return createTableOutputDataKey(text, type, editor, cellPointer, executionCount, isLastForCell)
    }
}
