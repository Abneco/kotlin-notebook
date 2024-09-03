// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.asSafely
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.util.LETS_PLOT_MIME
import org.jetbrains.kotlinx.jupyter.plugin.util.convertObject
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor
import org.jetbrains.plugins.notebooks.jupyter.nbformat.DisplayDataContainer
import com.intellij.notebooks.visualization.NotebookIntervalPointer


class PlotDataKeyExtractor: NotebookDisplayOutputDataKeyExtractor {
    fun extractKey(
        data: DisplayDataContainer,
        executionCount: Int?,
    ): LetsPlotOutputDataKey? {
        if (!KotlinNotebookApplicationOptions.get().showLetsPlotAsSwing) return null

        val dataObject = data.toV4Json()
        if (!dataObject.has(LETS_PLOT_MIME)) return null
        val plotValue = dataObject[LETS_PLOT_MIME].asSafely<ObjectNode>() ?: return null
        val swingEnabled = plotValue[SWING_ENABLED_KEY].asSafely<BooleanNode>()?.asBoolean() ?: true
        if (!swingEnabled) return null
        val plotType = plotValue[PLOT_TYPE_KEY].asSafely<TextNode>()?.asText() ?: return null
        return when(plotType) {
            "lets_plot_spec" -> plotValue["output"].asSafely<ObjectNode>()?.let { outputSpec ->
                val applyColorScheme = plotValue[APPLY_COLOR_SCHEME_KEY].asSafely<BooleanNode>()?.asBoolean() ?: true
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
        editor: EditorImpl,
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
