// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor

internal fun Project.retrieveJupyterFileEditor(vFile: VirtualFile): JupyterFileEditor?
        = FileEditorManager.getInstance(this).getSelectedEditor(vFile) as? JupyterFileEditor

internal fun Project.getCurrentEditorOrNull(): Editor? {
    return FileEditorManager.getInstance(this).selectedEditor?.safeAs<TextEditor>()?.editor
}

internal fun Project.getSelectedKotlinNotebookFileOrNull(): BackedNotebookVirtualFile? {
    val editor = getCurrentEditorOrNull() ?: return null
    return editor.virtualFile?.toKotlinNotebookBackedFile()
}