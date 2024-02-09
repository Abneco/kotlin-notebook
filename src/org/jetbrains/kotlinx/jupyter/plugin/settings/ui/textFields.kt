// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.text.JTextComponent

fun <T : JTextComponent, V: Any> Cell<T>.bindValueText(
    property: MutableProperty<V>,
    validate: (V) -> Boolean,
    valueGetter: (String) -> V?,
    valuePrinter: (V) -> String = { it.toString() },
): Cell<T> {
    return bindText(
        { valuePrinter(property.get()) },
        { stringValue ->
            valueGetter(stringValue)
                ?.takeIf(validate)
                ?.let { value -> property.set(value) }
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

fun <T : JTextComponent, V: Comparable<V>> Cell<T>.bindComparableIntervalToTextWithFixer(
    property: MutableProperty<V>,
    interval: ClosedRange<V>,
    valueGetter: (String) -> V?,
    valuePrinter: (V) -> String = { it.toString() },
): Cell<T> {
    val minValue = interval.start
    val maxValue = interval.endInclusive
    val defaultValue = property.get()

    val validator: (V) -> Boolean = { it in interval }
    require(validator(defaultValue))

    return bindValueText(property, validator, valueGetter, valuePrinter)
        .addTextFocusLostFixer { oldText ->
            val value = valueGetter(oldText)
            val newValue = when {
                value == null -> defaultValue
                value < minValue -> minValue
                value > maxValue -> maxValue
                else-> null
            }
            newValue?.toString()
        }
}
