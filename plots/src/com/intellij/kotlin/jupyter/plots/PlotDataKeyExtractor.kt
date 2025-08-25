// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor
import com.intellij.jupyter.core.jupyter.nbformat.DisplayDataContainer
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.jupyter.execution.util.convertObject
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.openapi.editor.Editor
import com.intellij.util.asSafely


class PlotDataKeyExtractor: NotebookDisplayOutputDataKeyExtractor {
    fun extractKey(
        data: DisplayDataContainer,
        executionCount: Int?,
    ): LetsPlotOutputDataKey? {
        if (!KotlinNotebookApplicationOptions.get().showLetsPlotAsSwing) return null

        val dataObject = data.toV4Json()
        if (!dataObject.has(MimeType.LETS_PLOT.mimeType)) return null
        val plotValue = dataObject[MimeType.LETS_PLOT.mimeType].asSafely<ObjectNode>() ?: return null
        val swingEnabled = plotValue[SWING_ENABLED_KEY].asSafely<BooleanNode>()?.asBoolean() != false
        if (!swingEnabled) return null
        val plotType = plotValue[PLOT_TYPE_KEY].asSafely<TextNode>()?.asText() ?: return null
        return when(plotType) {
            "lets_plot_spec" -> plotValue["output"].asSafely<ObjectNode>()?.let { outputSpec ->
                val applyColorScheme = plotValue[APPLY_COLOR_SCHEME_KEY].asSafely<BooleanNode>()?.asBoolean() != false
                LetsPlotOutputDataKey(
                    convertObject(outputSpec),
                    executionCount,
                    applyColorScheme,
                )
            }
            else -> null
        }
    }

    override fun extractKey(
        editor: Editor,
        file: BackedNotebookVirtualFile?,
        data: DisplayDataContainer,
        executionCount: Int?,
        cellPointer: NotebookIntervalPointer,
        isLastForCell: Boolean
    ): LetsPlotOutputDataKey? {
        return extractKey(data, executionCount)
    }

    companion object {
        private const val PLOT_TYPE_KEY = "output_type"
        private const val APPLY_COLOR_SCHEME_KEY = "apply_color_scheme"
        private const val SWING_ENABLED_KEY = "swing_enabled"
    }
}
