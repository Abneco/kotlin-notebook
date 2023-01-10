// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.isScript
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.NOTEBOOK_LANGUAGE
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebookBase
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

val VirtualFile?.isKotlinNotebook: Boolean get() {
    if (this == null || extension != "ipynb") return false
    return notebookLanguage === KotlinLanguage.INSTANCE
}

fun isKotlinNotebookInjectedFile(file: PsiFile?): Boolean {
    if (file == null) return false
    if (!file.isScript()) return false

    val service = JupyterCompilerService.getInstance(file.project)
    return file.name.endsWith(service.fileSuffix)
}


private val VirtualFile.notebookLanguage: Language? get(){
    BackedNotebookVirtualFile.takeIfBacked(this)?.let {
        return it.notebook.language
    }

    return if (this is LightVirtualFile) {
        // It's copy of either notebook or origin file being modified
        val notebookFile =
            originalFile?.let(BackedNotebookVirtualFile::takeIfBacked)
            ?: (originalFile as? LightVirtualFile)?.originalFile?.let(BackedNotebookVirtualFile::takeIfBacked)
        notebookFile?.notebook?.language
            ?: originalFile?.getUserData(NOTEBOOK_LANGUAGE)
            ?: getLanguageFromOriginalFile(this)
    }
    else {
        // It's origin file
        getUserData(NOTEBOOK_LANGUAGE)
            ?: getLanguageFromOriginalFile(this)?.let { language ->
                language.also { putUserData(NOTEBOOK_LANGUAGE, it) }
            }
    }
}

private fun getLanguageFromOriginalFile(file: VirtualFile): Language? {
    return try {
        JupyterNotebookBase.createJupyterNotebook(file.inputStream.reader())?.language
    } catch (e: Exception) {
        null
    }
}

internal fun PsiFile?.getNotebookCellList() =
    (this?.children?.first() as? JupyterNotebook)?.psiCellList

internal fun List<PsiLanguageInjectionHost>.getInjectedKtFiles(injectedLanguageManager: InjectedLanguageManager) =
    this.mapNotNull { h ->
        injectedLanguageManager.getInjectedPsiFiles(h)?.firstOrNull { it.first is KtFile }?.first as? KtFile
    }

internal fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)

internal fun Document.toPsiFile(project: Project): PsiFile? =
    PsiDocumentManager.getInstance(project).getPsiFile(this)

internal fun PsiFile.toDocument(project: Project): Document? =
    PsiDocumentManager.getInstance(project).getDocument(this)

internal fun PsiElement?.isInsideKotlinNotebookFile(): Boolean {
    val virtualFile = (this?.containingFile?.virtualFile as? VirtualFileWindow)?.delegate ?: return false
    return virtualFile.isKotlinNotebook
}

internal fun retrieveElementUnderCaret(scope: PsiFile): PsiElement? {
    val manager = FileEditorManager.getInstance(scope.project)
    val editor = manager.selectedEditor as? TextEditor ?: return null
    val caretOffSet = editor.editor.caretModel.offset
    val injectedManager = InjectedLanguageManager.getInstance(scope.project)
    val host = scope.findElementAt(caretOffSet)?.parentOfType<JupyterPsiCell>() as? PsiLanguageInjectionHost ?: return null
    val injectInfo = injectedManager.getInjectedPsiFiles(host)?.firstOrNull()?.first ?: return null

    return (injectInfo as? PsiFile)?.findElementAt(caretOffSet - host.startOffsetInParent - 5)
}

internal fun Document?.getNotebookCompleteAnalysisArea() =
    if (this?.getUserData(NotebookHighlightingUtilityObject.CompleteHighlightingRange) != null) {
        synchronized(this) { this.getUserData(NotebookHighlightingUtilityObject.CompleteHighlightingRange) }
    } else null


internal fun PsiFile.restartAnalyzing() {
    DaemonCodeAnalyzer.getInstance(this.project).restart(this)
}
