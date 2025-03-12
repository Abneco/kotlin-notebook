// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected

fun <T> SegmentedButtonComponent<T>.bindSelection(
    selectedProperty: ObservableMutableProperty<T>
) {
    selectedItem = selectedProperty.get()
    whenItemSelected { item ->
        selectedProperty.set(item)
    }
}
