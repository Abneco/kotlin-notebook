// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.asSafely
import org.jetbrains.kotlinx.jupyter.plugin.util.convertObject
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookObjectOutputDataKeyExtractor
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey

internal val letsPlotSwingOutputsEnabled: Boolean
    get() = Registry.`is`("lets.plot.swing.outputs.enabled", false)

class PlotDataKeyExtractor: NotebookObjectOutputDataKeyExtractor {
    override fun extractKey(dataObject: ObjectNode, executionCount: Int?): NotebookOutputDataKey? {
        if (!letsPlotSwingOutputsEnabled) return null
        if (!dataObject.has(PLOT_KEY)) return null
        val plotValue = dataObject[PLOT_KEY].asSafely<ObjectNode>() ?: return null
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

    companion object {
        private const val PLOT_KEY = "application/plot+json"
        private const val PLOT_TYPE_KEY = "output_type"
        private const val APPLY_COLOR_SCHEME_KEY = "apply_color_scheme"
        private const val SWING_ENABLED_KEY = "swing_enabled"
    }
}