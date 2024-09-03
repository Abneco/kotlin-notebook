// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterNotebookEditorAppearanceProvider
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.notebooks.visualization.NotebookEditorAppearanceProvider

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
    override fun shouldShowExecutionCounts(): Boolean = KotlinNotebookApplicationOptions.get().shouldShowExecutionCount

    override fun shouldShowOutExecutionCounts(): Boolean = true
}
