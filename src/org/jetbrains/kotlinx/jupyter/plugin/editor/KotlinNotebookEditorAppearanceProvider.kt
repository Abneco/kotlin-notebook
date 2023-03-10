// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptionsProvider
import org.jetbrains.plugins.notebooks.ui.editor.DefaultNotebookEditorAppearance
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookEditorAppearanceProvider

class KotlinNotebookEditorAppearanceProvider : NotebookEditorAppearanceProvider {
    override fun create(editor: Editor): NotebookEditorAppearance? {
        if (editor.isKotlinNotebook) {
            return KotlinNotebookEditorAppearance()
        }
        return null
    }
}

class KotlinNotebookEditorAppearance : NotebookEditorAppearance by DefaultNotebookEditorAppearance {
    private val applicationOptionsProvider get() = service<KotlinNotebookApplicationOptionsProvider>()

    override fun shouldShowExecutionCounts(): Boolean = applicationOptionsProvider.state.shouldShowExecutionCount
}