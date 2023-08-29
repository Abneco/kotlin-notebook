// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

object KotlinNotebookCodegen {
    fun generateColorSchemeChangeCode(): String {
        val isDark = uiFeelsDark() ?: return ""
        val themeName = if (isDark) {
            "DARK"
        } else {
            "LIGHT"
        }
        return "notebook.changeColorScheme(ColorScheme.$themeName)"
    }
}
