// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.HasExecutionCount

data class LetsPlotOutputDataKey(
    val spec: JsonObject,
    override val executionCount: Int?,
    val applyColorScheme: Boolean,
): HasExecutionCount {
    override fun getContentForDiffing(): Any {
        return JSON.encodeToString(spec)
    }

    companion object {
        private val JSON = Json { prettyPrint = true }
    }
}
