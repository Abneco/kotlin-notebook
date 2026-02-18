// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun Project.getJupyterFileEditor(vFile: VirtualFile): JupyterFileEditor?
        = FileEditorManager.getInstance(this).getEditors(vFile)
            .filterIsInstance<JupyterFileEditor>()
            .firstOrNull()

@OptIn(UnsafeCastFunction::class)
fun Project.getCurrentEditorOrNull(): Editor? {
    return FileEditorManager.getInstance(this).selectedEditor?.safeAs<TextEditor>()?.editor
}

internal fun VirtualFile.isCurrentlySelectedInEditor(project: Project): Boolean {
    return project.getCurrentEditorOrNull()?.virtualFile == this
}

fun BackedNotebookVirtualFile.isCurrentlySelectedInEditor(project: Project): Boolean {
    return file.isCurrentlySelectedInEditor(project)
}

internal fun Project.getSelectedKotlinNotebookFileOrNull(): BackedNotebookVirtualFile? {
    val editor = getCurrentEditorOrNull() ?: return null
    return editor.virtualFile?.toKotlinNotebookBackedFile()
}

internal fun Project.getOpenedKotlinNotebookEditors(): Collection<TextEditor> {
    val editorManager = FileEditorManager.getInstance(this)

    return editorManager.openFiles.mapNotNull {
        it.toKotlinNotebookBackedFile()
    }.mapNotNull {
        editorManager.getSelectedEditor(it.file) as? TextEditor
    }
}

fun Project.getOpenKotlinNotebookFiles(): Collection<BackedNotebookVirtualFile> {
    return getOpenedKotlinNotebookEditors().mapNotNull {
        it.file.toKotlinNotebookBackedFile()
    } ?: emptyList()
}

@RequiresEdt
fun Project.openNotebookEditor(file: BackedNotebookVirtualFile): TextEditor? {
    val editorManager = FileEditorManager.getInstance(this)
    return editorManager.openFile(file.file, true).firstOrNull() as? TextEditor
}

fun Editor.getTopLevelEditor(): Editor =
    when (this) {
        is EditorWindow -> delegate.getTopLevelEditor()
        else -> this
    }