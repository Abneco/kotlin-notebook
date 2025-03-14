// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected

class SegmentedButtonItem<T>(
    val value: T,
    val presentation: SegmentedButton.ItemPresentation,
)

inline fun <reified T: Enum<T>> createSegmentedButtonItems(
    getPresentation: (T) -> SegmentedButton.ItemPresentation,
): List<SegmentedButtonItem<T>> {
    return enumValues<T>().map { value ->
        SegmentedButtonItem(
            value,
            getPresentation(value)
        )
    }
}

fun <T> createSegmentedButton(items: List<SegmentedButtonItem<T>>): SegmentedButtonComponent<T> {
    val itemsByValue = items.associateBy { it.value }

    val segmentedButtonComponent = SegmentedButtonComponent<T> { value ->
        val item = itemsByValue[value] ?: error("No button item for value $value")
        item.presentation
    }

    return segmentedButtonComponent.apply {
        this.items = items.map { it.value }
        spacing = IntelliJSpacingConfiguration()
    }
}

fun <T> Cell<SegmentedButtonComponent<T>>.bindSelectionChanges(
    selectedProperty: ObservableMutableProperty<T>
): Cell<SegmentedButtonComponent<T>> {
    component.bindSelectionChanges(selectedProperty)
    return this
}

private fun <T> SegmentedButtonComponent<T>.bindSelectionChanges(
    selectedProperty: ObservableMutableProperty<T>
) {
    selectedItem = selectedProperty.get()
    whenItemSelected { item ->
        selectedProperty.set(item)
    }
    selectedProperty.afterChange {
        selectedItem = it
    }
}

/**
 * Binds [property] to this segmented button cell.
 */
fun <T: Any> Cell<SegmentedButtonComponent<T>>.bindSelection(
    property: MutableProperty<T>
): Cell<SegmentedButtonComponent<T>> {
    val defaultValue = property.get()
    bind(
        componentGet = { it.selectedItem ?: defaultValue },
        componentSet = { component, value -> component.selectedItem = value },
        prop = property
    )
    return this
}
