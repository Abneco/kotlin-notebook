// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.jupyter.core.jupyter.editor.JupyterNotebookEditorAppearanceProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.notebooks.visualization.NotebookEditorAppearanceProvider
import com.intellij.openapi.editor.Editor

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
