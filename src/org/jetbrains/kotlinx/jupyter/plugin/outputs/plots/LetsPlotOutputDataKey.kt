// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.fasterxml.jackson.databind.node.ObjectNode
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.HasExecutionCount
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey

data class LetsPlotOutputDataKey(
    val spec: ObjectNode,
    override val executionCount: Int?
): HasExecutionCount {
    override fun getContentForDiffing(): Any {
        return spec.toPrettyString()
    }
}
