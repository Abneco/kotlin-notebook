// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptionsProvider
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterNotebookEditorAppearanceProvider
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookEditorAppearanceProvider

class KotlinNotebookEditorAppearanceProvider : NotebookEditorAppearanceProvider {
    override fun create(editor: Editor): NotebookEditorAppearance? {
        if (editor.isKotlinNotebook) {
            val appearanceProvider = NotebookEditorAppearanceProvider.EP_NAME.findExtensionOrFail(JupyterNotebookEditorAppearanceProvider::class.java)
            val appearance = appearanceProvider.create(editor) ?: return null
            return KotlinNotebookEditorAppearance(appearance)
        }
        return null
    }
}

class KotlinNotebookEditorAppearance(delegate: NotebookEditorAppearance) : NotebookEditorAppearance by delegate {
    private val applicationOptionsProvider get() = service<KotlinNotebookApplicationOptionsProvider>()

    override fun shouldShowExecutionCounts(): Boolean = applicationOptionsProvider.state.shouldShowExecutionCount
}
