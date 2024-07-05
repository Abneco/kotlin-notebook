// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.tables.createTableOutputDataKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.scientific.tables.api.DSTableDataType
import com.intellij.scientific.tables.api.DSTableText
import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.util.KOTLIN_DATAFRAME_MIME
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterBrowserOutputDataKey
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterOutputDataKeyExtractor
import com.intellij.jupyter.tables.JupyterTableOutputDataKey
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.getOutputsForIndex
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.webOutputs.JupyterWebOutputInfo
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterExecuteResultOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterOutputType
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointerFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor

/**
 * Extract [JupyterTableOutputDataKey] from Kotlin Dataframe produced cell output
 */
class KotlinDataframeOutputDataKeyExtractor : NotebookOutputDataKeyExtractor {
    private val jupyterDelegate: JupyterOutputDataKeyExtractor = JupyterOutputDataKeyExtractor()

    override fun extract(editor: EditorImpl, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>? {
        val res = when {
            !KotlinNotebookApplicationOptions.get().showDataFrameAsSwing -> null
            !editor.isJupyter -> null
            interval.language != KotlinLanguage.INSTANCE -> null
            else -> extractImpl(editor, interval)
        }

        if (res.isNullOrEmpty()) return null

        val jupyterKeys = jupyterDelegate.extract(editor, interval)

        return (jupyterKeys?.filterNot { isBrowserTableOutputKey(it) } ?: emptyList()) + res
    }

    private fun isBrowserTableOutputKey(key: NotebookOutputDataKey): Boolean {
        if (key !is JupyterBrowserOutputDataKey) return false
        val info = key.info
        if (info !is JupyterWebOutputInfo.Output) return false

        return info.output[KOTLIN_DATAFRAME_MIME] != null
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

        val serializedDataframe = dataObject.toString()

        val text = DSTableText(staticTableText = serializedDataframe, plainText = serializedDataframe)
        val type = DSTableDataType.EXTERNAL

        return createTableOutputDataKey(text, type, editor, cellPointer, executionCount, isLastForCell)
    }
}