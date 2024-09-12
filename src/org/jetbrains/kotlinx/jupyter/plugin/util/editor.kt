// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun Project.getJupyterFileEditor(vFile: VirtualFile): JupyterFileEditor?
        = FileEditorManager.getInstance(this).getSelectedEditor(vFile) as? JupyterFileEditor

internal fun Project.getCurrentEditorOrNull(): Editor? {
    return FileEditorManager.getInstance(this).selectedEditor?.safeAs<TextEditor>()?.editor
}

internal fun VirtualFile.isCurrentlySelectedInEditor(project: Project): Boolean {
    return project.getCurrentEditorOrNull()?.virtualFile == this
}

internal fun BackedNotebookVirtualFile.isCurrentlySelectedInEditor(project: Project): Boolean {
    return file.isCurrentlySelectedInEditor(project)
}

internal fun Project.getSelectedKotlinNotebookFileOrNull(): BackedNotebookVirtualFile? {
    val editor = getCurrentEditorOrNull() ?: return null
    return editor.virtualFile?.toKotlinNotebookBackedFile()
}

internal fun Project.getOpenedKotlinNotebookEditors(): Collection<TextEditor>? {
    val editorManager = FileEditorManager.getInstance(this)

    return editorManager.openFiles.mapNotNull {
        it.toKotlinNotebookBackedFile()
    }.mapNotNull {
        editorManager.getSelectedEditor(it.file) as? TextEditor
    }.ifEmpty { return null }
}