// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterBrowserOutputDataKey
import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.editor.outputs.getOutputsForIndex
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.JupyterWebOutputInfo
import com.intellij.jupyter.core.jupyter.helper.isJupyter
import com.intellij.jupyter.core.jupyter.nbformat.JupyterExecuteResultOutput
import com.intellij.jupyter.core.jupyter.nbformat.JupyterOutputType
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.jupyter.tables.JupyterTableOutputDataKey
import com.intellij.jupyter.tables.createTableOutputDataKey
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.scientific.tables.api.DSTableDataType
import com.intellij.scientific.tables.api.DSTableText
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Extract [JupyterTableOutputDataKey] from Kotlin Dataframe produced cell output
 */
class KotlinDataframeOutputDataKeyExtractor : NotebookOutputDataKeyExtractor {
    private val jupyterDelegate: JupyterOutputDataKeyExtractor = JupyterOutputDataKeyExtractor()

    override fun extract(editor: Editor, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>? {
        val res = when {
            !KotlinNotebookApplicationOptions.get().showDataFrameAsSwing -> null
            !editor.isJupyter -> null
            interval.language != KotlinLanguage.INSTANCE -> null
            else -> extractImpl(editor as EditorImpl, interval)
        }

        if (res.isNullOrEmpty()) return null

        val jupyterKeys = jupyterDelegate.extract(editor, interval)

        return (jupyterKeys?.filterNot { isBrowserTableOutputKey(it) } ?: emptyList()) + res
    }

    private fun isBrowserTableOutputKey(key: NotebookOutputDataKey): Boolean {
        if (key !is JupyterBrowserOutputDataKey) return false
        val info = key.info
        if (info !is JupyterWebOutputInfo.Output) return false

        val jsonOutput = jackson.readTree(info.output) as ObjectNode
        return jsonOutput[MimeType.KOTLIN_DATAFRAME.mimeType] != null
    }

    private fun extractImpl(
        editor: EditorImpl,
        interval: NotebookCellLines.Interval
    ): List<NotebookOutputDataKey> {
        val outputSequence = getOutputsForIndex(editor, interval.ordinal)?.outputs ?: return emptyList()
        val cellPointer = NotebookIntervalPointerFactory.get(editor).create(interval)

        val result = ArrayList<NotebookOutputDataKey>()
        for ((outputIndex, output) in outputSequence.withIndex()) {
            if (output.outputType != JupyterOutputType.EXECUTE_RESULT &&
                output.outputType != JupyterOutputType.DISPLAY_DATA
            ) {
                continue
            }
            output.json["data"]?.asSafely<ObjectNode>()?.let { dataObject ->
                val executionCount = (output as? JupyterExecuteResultOutput)?.executionCount

                val kotlinDfDataKey = getKotlinDataframeOutputDataKey(
                    editor = editor,
                    cellPointer = cellPointer,
                    dataObject = dataObject,
                    executionCount = executionCount,
                    isLastForCell = outputIndex == outputSequence.size - 1
                )

                if (kotlinDfDataKey != null) {
                    result.add(kotlinDfDataKey)
                }
            }
        }

        return result
    }

    private fun getKotlinDataframeOutputDataKey(
        editor: EditorImpl,
        cellPointer: NotebookIntervalPointer,
        dataObject: ObjectNode,
        executionCount: Int?,
        isLastForCell: Boolean
    ): NotebookOutputDataKey? {
        if (!KotlinDataframeParsing.isKotlinDataFrame(dataObject)) {
            return null
        }
        if (KotlinDataframeParsing.dataFrameIsFormatted(dataObject)) {
            return null
        }

        val serializedDataframe = dataObject.toString()

        val text = DSTableText(staticTableText = serializedDataframe, plainText = serializedDataframe)
        val type = DSTableDataType.EXTERNAL

        return createTableOutputDataKey(text, type, editor, cellPointer, executionCount, isLastForCell)
    }
}