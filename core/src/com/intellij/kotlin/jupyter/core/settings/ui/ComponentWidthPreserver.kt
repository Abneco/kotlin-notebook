// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import java.awt.Component
import java.awt.Dimension
import javax.swing.JLabel

class ComponentWidthPreserver {
    private var maxWidth: Int = 0
    private val components = mutableListOf<Component>()

    fun addComponent(component: Component) {
        components.add(component)
        maxWidth = maxOf(maxWidth, component.preferredSize.width)

        for (component in components) {
            val minSize = component.minimumSize
            component.minimumSize = Dimension(maxOf(maxWidth, minSize.width), minSize.height)
        }
    }
}

fun Row.alignedWidthLabel(
    preserver: ComponentWidthPreserver,
    @NlsContexts.Label text: String,
): Cell<JLabel> {
    return label(text).apply {
        preserver.addComponent(component)
    }
}
