// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing

import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.HasExecutionCount
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.statistic.NotebookOutputKeyType

/**
 * [NotebookOutputDataKey] representing a custom in-memory Swing output.
 *
 * Make sure to only put a [component] into this class that can be displayed
 * by [SwingComponent].
 */
class SwingOutputDataKey(
    val component: Any,
    override val executionCount: Int?,
): HasExecutionCount {
    override val statisticKey = NotebookOutputKeyType.SWING_COMPONENT
    val content = component.toString() // Snapshot `toString()` to prevent modifications during use of the UI.
    override fun getContentForDiffing(): Any = content
}
