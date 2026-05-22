// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.editor.outputs.getOutputsForIndex
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.JupyterBrowserOutputDataKey
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterExecuteResultOutput
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterOutputType
import com.intellij.jupyter.tables.JupyterTableOutputDataKey
import com.intellij.jupyter.tables.createTableOutputDataKey
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.NotebookIntervalPointerFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKeyExtractor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.scientific.tables.api.DSTableDataType
import com.intellij.scientific.tables.api.DSTableText
import com.intellij.util.asSafely

/**
 * Extract [JupyterTableOutputDataKey] from Kotlin Dataframe produced cell output
 */
class KotlinDataframeOutputDataKeyExtractor : NotebookOutputDataKeyExtractor {
    private val jupyterDelegate: JupyterOutputDataKeyExtractor = JupyterOutputDataKeyExtractor()
    override fun extract(
        project: Project,
        file: VirtualFile,
        interval: NotebookCellLines.Interval,
    ): List<NotebookOutputDataKey>? {
        val notebookFile = BackedNotebookVirtualFile.takeIfBacked(file) ?: return null
        val res = when {
            !KotlinNotebookApplicationOptions.get().showDataFrameAsSwing -> null
            interval.type != CellType.CODE -> null
            else -> extractImpl(project, notebookFile, interval)
        }

        if (res.isNullOrEmpty()) return null

        val jupyterKeys = jupyterDelegate.extract(project, notebookFile.file, interval)

        return (jupyterKeys?.filterNot { isBrowserTableOutputKey(it) } ?: emptyList()) + res

    }

    private fun isBrowserTableOutputKey(key: NotebookOutputDataKey): Boolean {
        if (key !is JupyterBrowserOutputDataKey) return false
        val info = key.info

        val jsonOutput = jackson.readTree(info.output) as ObjectNode
        return jsonOutput[MimeType.KOTLIN_DATAFRAME.mimeType] != null
    }

    private fun extractImpl(
        project: Project,
        notebookVirtualFile: BackedNotebookVirtualFile,
        interval: NotebookCellLines.Interval,
    ): List<NotebookOutputDataKey> {
        val outputSequence = getOutputsForIndex(notebookVirtualFile, interval.ordinal)?.outputs ?: return emptyList()
        val document = runReadAction {
            FileDocumentManager.getInstance().getDocument(notebookVirtualFile.file)
        } ?: return emptyList()
        val cellPointer = NotebookIntervalPointerFactory.get(project, document).create(interval)

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
                    project = project,
                    notebookVirtualFile = notebookVirtualFile,
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
        notebookVirtualFile: BackedNotebookVirtualFile,
        cellPointer: NotebookIntervalPointer,
        dataObject: ObjectNode,
        executionCount: Int?,
        isLastForCell: Boolean,
        project: Project,
    ): NotebookOutputDataKey? {
        if (!KotlinDataframeParsing.isKotlinDataFrame(dataObject)) {
            return null
        }
        // formatted dataframes (`FormattedFrame`) need to be rendered as HTML to show their adapted cell attributes,
        // so we return `null` here when it has `is_formatted: true` in its metadata.
        if (KotlinDataframeParsing.isFormattedDataFrame(dataObject)) {
            return null
        }

        val serializedDataframe = dataObject.toString()

        val text = DSTableText(staticTableText = serializedDataframe, plainText = serializedDataframe)
        val type = DSTableDataType.EXTERNAL

        return createTableOutputDataKey(project, text, type, notebookVirtualFile, cellPointer, executionCount, isLastForCell)
    }
}