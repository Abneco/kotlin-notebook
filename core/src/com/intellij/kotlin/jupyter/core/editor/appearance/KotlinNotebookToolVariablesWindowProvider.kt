// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.kotlin.jupyter.core.editor.appearance.data.KotlinNotebookVariablesToolWindowConfiguration
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.content.Content


/**
 * Provides UI content for the variables' tool window.
 * Right now the debug sub-plugin provides the current implementation.
 */
interface KotlinNotebookToolVariablesUiContentProvider {
    fun createVariablesViewContent(configuration: KotlinNotebookVariablesToolWindowConfiguration): Content?

    companion object {
        private val EP = ExtensionPointName.create<KotlinNotebookToolVariablesUiContentProvider>("com.intellij.kotlin.jupyter.core.notebookVariablesUiContentProvider")

        fun createVariablesViewContent(configuration: KotlinNotebookVariablesToolWindowConfiguration): Content? {
            return EP.extensionList.firstNotNullOfOrNull {
                it.createVariablesViewContent(configuration)
            }
        }
    }
}