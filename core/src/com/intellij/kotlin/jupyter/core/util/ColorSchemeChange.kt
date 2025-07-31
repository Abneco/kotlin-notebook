// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import org.jetbrains.kotlinx.jupyter.api.libraries.ColorScheme

fun generateColorSchemeChangeCode(
    theme: ColorScheme = getNotebookTheme(),
): String {
    return "notebook.changeColorScheme(ColorScheme.${theme.name})"
}

fun getNotebookTheme(): ColorScheme {
    return if (uiFeelsDark()) {
        ColorScheme.DARK
    } else {
        ColorScheme.LIGHT
    }
}
