// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.swing

import com.intellij.jupyter.core.jupyter.editor.outputs.createGutterPainter
import com.intellij.jupyter.core.jupyter.editor.outputs.updateGutterPainter
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
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
        output: SwingOutputDataKey
    ): NotebookOutputComponentFactory.CreatedComponent<SwingComponent> {
        val component = SwingComponent()
        component.initialize(editor, output)
        return NotebookOutputComponentFactory.CreatedComponent(
            component,
            NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE,
            output.createGutterPainter(),
            limitHeight = false,
            resizable = true,
            { KotlinNotebookBundle.message("kotlin.notebook.collapsed.swing.component.output.text") },
            null
        )
    }

    override fun updateComponent(editor: EditorImpl, component: SwingComponent, outputDataKey: SwingOutputDataKey) {
        outputDataKey.updateGutterPainter(component.gutterPainter)
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
