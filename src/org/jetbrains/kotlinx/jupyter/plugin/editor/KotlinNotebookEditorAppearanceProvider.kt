// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.plugins.notebooks.ui.editor.DefaultNotebookEditorAppearance
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookEditorAppearanceProvider

class KotlinNotebookEditorAppearanceProvider : NotebookEditorAppearanceProvider {
    override fun create(editor: Editor): NotebookEditorAppearance? {
        val project = editor.project ?: return null
        if (editor.isKotlinNotebook) {
            return KotlinNotebookEditorAppearance(project)
        }
        return null
    }
}

class KotlinNotebookEditorAppearance(private val project: Project) : NotebookEditorAppearance by DefaultNotebookEditorAppearance {
    private val optionsProvider get() = KotlinNotebookProjectOptionsProvider.getInstance(project)

    override fun shouldShowExecutionCounts(): Boolean = optionsProvider.state.shouldShowExecutionCount
}