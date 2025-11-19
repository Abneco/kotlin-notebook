// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.jupyter.core.jupyter.editor.outputs.HasExecutionCount
import com.intellij.notebooks.visualization.outputs.statistic.NotebookOutputKeyType
import kotlinx.serialization.json.Json
import org.jetbrains.letsPlot.toolkit.json.JsonMap
import org.jetbrains.letsPlot.toolkit.json.serializeJsonMap

data class LetsPlotOutputDataKey(
    val spec: JsonMap,
    override val executionCount: Int?,
    val applyColorScheme: Boolean,
) : HasExecutionCount {
    override fun getStatisticKey(): NotebookOutputKeyType = NotebookOutputKeyType.LETS_PLOT
    override fun getContentForDiffing(): Any {
        return JSON.encodeToString(serializeJsonMap(spec))
    }

    companion object {
        private val JSON by lazy { Json { prettyPrint = true } }
    }
}