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

    fun generateSessionOptions(
        resolveSources: Boolean? = null,
        serializeScriptData: Boolean? = null,
        resolveMpp: Boolean? = null
    ): String {
        fun gen(key: String, value: Boolean?): String? {
            if (value == null) return null
            return "SessionOptions.$key = $value"
        }

        return listOfNotNull(
            gen("resolveSources", resolveSources),
            gen("serializeScriptData", serializeScriptData),
            gen("resolveMpp", resolveMpp)
        ).joinToString("\n")
    }
}
