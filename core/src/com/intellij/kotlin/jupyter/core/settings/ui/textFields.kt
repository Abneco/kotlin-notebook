// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.text
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.JTextField
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
    ).onReset {
        text(valuePrinter(property.get()))
    }
}

fun <T : JTextComponent> Cell<T>.bindStringText(
    property: MutableProperty<String>,
    validate: (String) -> Boolean = { true },
): Cell<T> {
    return bindValueText(property, validate, { it }, { it })
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindStringText(
    property: MutableProperty<String>,
): Cell<T> {
    return bindText(property).onReset {
        text(property.get())
    }
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

@Suppress("HardCodedStringLiteral")
fun <T : JComponent> Cell<T>.reactiveComment(textProperty: ObservableMutableProperty<String>): Cell<T> {
    val comment = comment(textProperty.get()).comment!!
    textProperty.afterChange { newValue ->
        comment.text = newValue
    }
    return this
}

fun <T : JTextField> Cell<T>.bindReactiveText(textProperty: ObservableMutableProperty<String>): Cell<T> {
    component.text = textProperty.get()
    onChanged {
        textProperty.set(component.text)
    }
    textProperty.afterChange { newValue ->
        component.text = newValue
    }
    return this
}

fun <T : JTextField> Cell<T>.reactiveValidation(validationInfoProperty: ObservableProperty<ValidationInfo?>): Cell<T> {
    validation(DialogValidation {
        validationInfoProperty.get()
    }).validationRequestor { parentDisposable, validate ->
        validationInfoProperty.afterChange(parentDisposable) { validate() }
    }
    return this
}
