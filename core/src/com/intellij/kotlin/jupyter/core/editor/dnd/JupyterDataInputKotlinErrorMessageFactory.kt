// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.jupyter.data.input.run.JupyterDataInputErrorMessageFactory
import com.intellij.lang.Language
import org.jetbrains.kotlin.idea.KotlinLanguage

private class JupyterDataInputKotlinErrorMessageFactory : JupyterDataInputErrorMessageFactory {
    override fun makeErrorMessage(text: String, language: Language): String? {
        if (language != KotlinLanguage.INSTANCE) return null

        // TODO handle cases when text has " or other special symbols
        return "throw Exception(\"$text\")"
    }
}