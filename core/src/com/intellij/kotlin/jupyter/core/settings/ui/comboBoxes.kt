// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import javax.swing.ListCellRenderer

inline fun <reified T: Enum<T>> Row.enumComboBox(
    renderer: ListCellRenderer<in T?>? = null,
): Cell<ComboBox<T>> {
    return comboBox(enumValues<T>().toList(), renderer)
}
