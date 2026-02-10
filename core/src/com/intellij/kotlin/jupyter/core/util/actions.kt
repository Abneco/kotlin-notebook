// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.ide.actions.RevealFileAction
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal fun DataContext.getVirtualFile(): VirtualFile? = CommonDataKeys.VIRTUAL_FILE.getData(this)
internal fun AnActionEvent.getVirtualFile(): VirtualFile? = dataContext.getVirtualFile()
internal fun DataContext.getLastActiveFileEditor(): FileEditor? = PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(this)
internal fun DataContext.getLastActiveJupyterFileEditor(): JupyterFileEditor? = PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(this) as? JupyterFileEditor
internal fun AnActionEvent.getLastActiveFileEditor(): FileEditor? = dataContext.getLastActiveFileEditor()

fun DataContext.getKotlinNotebookVirtualFile(): BackedNotebookVirtualFile? {
    val virtualFile = when (val vFile = getVirtualFile()) {
        null -> getLastActiveJupyterFileEditor()?.getNotebookFile()
        else -> vFile
    }
    return virtualFile?.toKotlinNotebookBackedFile()
}

fun AnActionEvent.getKotlinNotebookJupyterFile(): JupyterNotebook? {
    val notebookFile = notebookFile ?: return null
    return if (notebookFile.isKotlinNotebook) {
        notebookFile.notebookOrNull
    } else {
        null
    }
}

internal fun AnActionEvent.getKotlinNotebookVirtualFile(): BackedNotebookVirtualFile? = dataContext.getKotlinNotebookVirtualFile()

internal fun Project.revealKotlinNotebookLocalKernelsFolder() {
    val path = KotlinNotebookMavenArtifactsDownloader.getInstance(this).kernelsDirectoryPath
    RevealFileAction.openDirectory(path)
}