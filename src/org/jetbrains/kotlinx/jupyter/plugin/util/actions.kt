// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.ide.actions.RevealFileAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.refactoring.project
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.plugins.notebooks.core.api.getNotebookPsiCell
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor

internal fun DataContext.getVirtualFile(): VirtualFile? = CommonDataKeys.VIRTUAL_FILE.getData(this)
internal fun AnActionEvent.getVirtualFile(): VirtualFile? = dataContext.getVirtualFile()
internal fun DataContext.getLastActiveFileEditor(): FileEditor? = PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(this)
internal fun DataContext.getLastActiveJupyterFileEditor(): JupyterFileEditor? = PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.getData(this) as? JupyterFileEditor
internal fun AnActionEvent.getLastActiveFileEditor(): FileEditor? = dataContext.getLastActiveFileEditor()

internal fun DataContext.getKotlinNotebookVirtualFile(): BackedNotebookVirtualFile? {
    val virtualFile = when (val vFile = getVirtualFile()) {
        null -> getLastActiveJupyterFileEditor()?.getNotebookFile()
        else -> vFile
    }
    if (virtualFile == null) {
        return null
    }
    if (!virtualFile.isKotlinNotebook) return null
    return BackedNotebookVirtualFile.takeIfBacked(virtualFile)
}

internal fun DataContext.getInjectedKtFilesInCurrentPsiCell(): List<KtFile>? {
    return (getNotebookPsiCell() as? PsiLanguageInjectionHost)?.getInjectedKtFiles(
        InjectedLanguageManager.getInstance(project)
    )
}

internal fun AnActionEvent.getKotlinNotebookVirtualFile(): BackedNotebookVirtualFile? = dataContext.getKotlinNotebookVirtualFile()

internal fun Project.revealKotlinNotebookLocalKernelsFolder() {
    val path = KotlinNotebookMavenArtifactsDownloader.getInstance(this).kernelsDirectoryPath
    RevealFileAction.openDirectory(path)
}