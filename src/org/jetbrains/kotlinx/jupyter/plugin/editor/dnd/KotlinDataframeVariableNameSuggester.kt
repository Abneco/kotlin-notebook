// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.dnd

import com.intellij.lang.LanguageNamesValidation
import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.editor.handlers.DataframeVariableNameSuggester

object KotlinDataframeVariableNameSuggester : DataframeVariableNameSuggester {
    override fun suggestVariableName(fileNameWithoutExtension: String): String {
        return fileNameWithoutExtension.toCamelCase()
    }

    private val namesValidator: NamesValidator =
        LanguageNamesValidation.INSTANCE.forLanguage(KotlinLanguage.INSTANCE)

    private val onlyLatinRegex = Regex("[a-zA-Z0-9_]+")

    override fun isValidIdentifier(name: String, project: Project?): Boolean {
        return onlyLatinRegex.matches(name) && namesValidator.isIdentifier(name, project)
    }
}
