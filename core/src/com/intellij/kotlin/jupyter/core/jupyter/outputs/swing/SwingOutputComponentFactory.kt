// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.swing

import com.intellij.jupyter.core.jupyter.editor.outputs.createExecutionCountHolder
import com.intellij.jupyter.core.jupyter.editor.outputs.updateExecutionCountHolder
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.executionCountHolder
import com.intellij.openapi.editor.impl.EditorImpl

/**
 * Component factory for creating in-memory Swing components. These are only
 * available when using an embedded kernel.
 */
class SwingOutputComponentFactory: NotebookOutputComponentFactory<SwingComponent, SwingOutputDataKey> {

    override val componentClass: Class<SwingComponent>
        get() = SwingComponent::class.java
    override val outputDataKeyClass: Class<SwingOutputDataKey>
        get() = SwingOutputDataKey::class.java

    override fun createComponent(
        editor: EditorImpl,
        outputDataKey: SwingOutputDataKey
    ): NotebookOutputComponentFactory.CreatedComponent<SwingComponent> {
        val component = SwingComponent()
        component.initialize(editor, outputDataKey)
        return NotebookOutputComponentFactory.CreatedComponent(
            component,
            NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE,
            limitHeight = false,
            resizable = true,
            { KotlinNotebookBundle.message("kotlin.notebook.collapsed.swing.component.output.text") },
            outputDataKey.createExecutionCountHolder(),
            null
        )
    }

    override fun updateComponent(editor: EditorImpl, component: SwingComponent, outputDataKey: SwingOutputDataKey) {
        outputDataKey.updateExecutionCountHolder(component.executionCountHolder)
        component.initialize(editor, outputDataKey)
    }

    override fun match(component: SwingComponent, outputDataKey: SwingOutputDataKey): NotebookOutputComponentFactory.Match {
        return if (component.dataKey == outputDataKey) {
            NotebookOutputComponentFactory.Match.SAME
        } else {
            NotebookOutputComponentFactory.Match.NONE
        }
    }
}