// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.text.JTextComponent

fun <T : JTextComponent> Cell<T>.bindDoubleText(
    property: MutableProperty<Double>,
    validate: (Double) -> Boolean,
): Cell<T> {
    return bindText(
         { property.get().toString() },
         { value ->
             value.toDoubleOrNull()
                 ?.takeIf(validate)
                 ?.let { doubleValue -> property.set(doubleValue) }
         }
    )
}

fun <T : JTextComponent> Cell<T>.addTextFocusLostFixer(
    fixer: (oldText: String) -> String?,
): Cell<T> {
    return applyToComponent {
        addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
            }

            override fun focusLost(e: FocusEvent?) {
                text = fixer(text) ?: return
            }
        })
    }
}
