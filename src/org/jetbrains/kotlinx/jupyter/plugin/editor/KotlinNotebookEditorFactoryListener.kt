// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.editor.NotebookEditorCreatedCallback
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter

class KotlinNotebookEditorFactoryListener : NotebookEditorCreatedCallback {
    override fun editorCreated(editor: Editor) {
        if (editor.isJupyter) {
            editor as EditorImpl
            val file = FileDocumentManager.getInstance().getFile(editor.document)
            if (file.isKotlinNotebook) {
                val project = editor.project ?: return
                JupyterKtScriptingSupport.update(project)
            }
        }
    }
}