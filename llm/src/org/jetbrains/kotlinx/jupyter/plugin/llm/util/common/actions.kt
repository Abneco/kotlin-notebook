// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.util.common

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.core.chat.messages.impl.FunctionNotFound
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile


suspend fun Project.retrieveCurrentEditor(): Editor? {
    return readAction {
        FileEditorManager.getInstance(this).selectedTextEditor ?: return@readAction null
    }
}

suspend fun Project.retrieveCurrentPsiFile(): PsiFile? {
    return readAction {
        val selectedTextEditor = FileEditorManager.getInstance(this).selectedTextEditor
        val document = selectedTextEditor?.document ?: return@readAction null
        PsiDocumentManager.getInstance(this).getPsiFile(document) ?: return@readAction null
    }
}

object CommonReusableResultFunctions {
    val fileIsNotKtNotebookError = FunctionCallResult.Error(
        FunctionNotFound(
            "Opened file inside the editor is not Kotlin Notebook"
        )
    )

    val variablesAreNotExisting = FunctionCallResult.Success(
        "There is no available context for current session"
    )

    val variableDataIsNotFound = FunctionCallResult.Success(
        "Can't get requested information about the variable"
    )

}

suspend fun Project.findVirtualFile(fileName: String): VirtualFile? {
    val directory = readAction { guessProjectDir() } ?: return null
    return VirtualFileManager.getInstance().findFileByUrl("${directory.path}/$fileName")
}

suspend fun Project.retrieveCurrentBackedNotebookFileOrNull(): BackedNotebookVirtualFile? {
    val psiFile = retrieveCurrentPsiFile() ?: return null
    return psiFile.virtualFile.toBackedKotlinNotebookOrNull()
}

internal fun VirtualFile?.toBackedKotlinNotebookOrNull(): BackedNotebookVirtualFile? {
    if (this == null) return null
    val originalFile = if (this is VirtualFileWindow) {
        this.delegate
    } else this
    val backedNotebook = originalFile.toBackedNotebookFile()
    if (backedNotebook == null || !backedNotebook.file.isKotlinNotebook) {
        return null
    }

    return backedNotebook
}