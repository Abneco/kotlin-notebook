// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.creating

import com.intellij.jupyter.core.editor.NotebookEditorCreatedCallback
import com.intellij.jupyter.core.jupyter.helper.isJupyter
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager

class KotlinNotebookEditorFactoryListener : NotebookEditorCreatedCallback {
    override fun editorCreated(editor: Editor) {
        val project = editor.project ?: return

        if (editor.isJupyter) {
            editor as EditorImpl
            val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
            if (file.isKotlinNotebook) {
                KotlinNotebookPerFileSettingsCache.getInstance(project).notebookEditorCreated(file)

                val notebookFile = file.toBackedNotebookFile() ?: return
                KotlinNotebookFeatureUsagesCollector.registerOpenNotebook(project, notebookFile)
            }
        }
    }
}