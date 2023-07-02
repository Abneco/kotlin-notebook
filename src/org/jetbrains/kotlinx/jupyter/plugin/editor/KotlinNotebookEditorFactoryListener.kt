// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.toBackedNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookPerFileSettingsCache
import org.jetbrains.kotlinx.jupyter.plugin.stats.KotlinNotebookFeatureUsagesCollector
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.editor.NotebookEditorCreatedCallback
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter

class KotlinNotebookEditorFactoryListener : NotebookEditorCreatedCallback {
    override fun editorCreated(editor: Editor) {
        val project = editor.project ?: return

        if (editor.isJupyter) {
            editor as EditorImpl
            val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
            if (file.isKotlinNotebook) {
                JupyterKtScriptingSupport.update(project)
                KotlinNotebookPerFileSettingsCache.getInstance(project).notebookEditorCreated(file)

                val backedNotebookVirtualFile = file.toBackedNotebookFile() ?: return
                val notebook = backedNotebookVirtualFile.notebook
                KotlinNotebookFeatureUsagesCollector.registerOpenNotebook(project, notebook)
            }
        }
    }
}