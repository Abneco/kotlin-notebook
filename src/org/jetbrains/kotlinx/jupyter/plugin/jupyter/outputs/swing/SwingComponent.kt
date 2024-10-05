// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * Component responsible for rendering in-memory Swing components
 * created by the user of the notebook.
 *
 * Note, this component will inherit the Editors look and feel
 * which might be different from when the code is run independently.
 */
class SwingComponent : JPanel(FlowLayout(FlowLayout.LEFT)) {
    private var _dataKey: SwingOutputDataKey? = null
    val dataKey: SwingOutputDataKey? get() = _dataKey

    init {
        this.isOpaque = false
    }

    fun initialize(editor: EditorImpl, dataKey: SwingOutputDataKey) {
        _dataKey = dataKey
        when (val component = dataKey.component) {
            is JDialog -> {
                val labelText = jDialogLabelText(component)
                val label = createLabel(editor, labelText)
                add(label, 0)
            }
            is JFrame -> {
                val labelText = jFrameLabelText(component)
                val label = createLabel(editor, labelText)
                add(label,  -1)
            }
            is JComponent -> {
                if (isInvisible(component)) {
                    val labelText = KotlinNotebookBundle.message("kotlin.notebook.collapsed.swing.component.no_size.text")
                    val label = createLabel(editor, labelText)
                    add(label,  -1)
                } else {
                    add(component, -1)
                }
            }
            else -> {
                throw IllegalStateException("Unsupported Swing component: $component")
            }
        }
        invalidate()
    }

    private fun isInvisible(component: Component) = !component.isVisible || component.size == Dimension(0, 0)

    private fun jDialogLabelText(dialog: JDialog): String {
        val invisible = isInvisible(dialog)
        return KotlinNotebookBundle.message("kotlin.notebook.outputs.swing.component.dialog${if (invisible) ".no.size" else ""}.text")
    }

    private fun jFrameLabelText(frame: JFrame): String {
        val invisible: Boolean = isInvisible(frame)
        return KotlinNotebookBundle.message("kotlin.notebook.outputs.swing.component.frame${if (invisible) ".no.size" else ""}.text", frame.title)
    }

    private fun createLabel(editor: EditorImpl, @NlsSafe labelText: String): JBLabel {
        return JBLabel(labelText).apply {
            foreground = editor.colorsScheme.defaultForeground
        }
    }
}
