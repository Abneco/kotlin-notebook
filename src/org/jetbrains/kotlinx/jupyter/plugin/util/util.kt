// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile.Companion.find
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.NotebookMetadataLanguageProvider
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebookBase
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

const val JUPYTER_NOTEBOOK_EXTENSION = "ipynb"
const val DEFAULT_KOTLIN_KERNEL_NAME = "kotlin"

val VirtualFile?.isKotlinNotebook: Boolean
    get() {
        if (this == null || extension != JUPYTER_NOTEBOOK_EXTENSION) return false
        return notebookLanguage === KotlinLanguage.INSTANCE
    }

val Editor.isKotlinNotebook: Boolean
    get() {
        if (this !is EditorEx) return false
        return FileDocumentManager.getInstance().getFile(document).isKotlinNotebook
    }

val KtFile.isInsideKotlinNotebook: Boolean
    get() {
        val vFile = virtualFile ?: return false
        if (vFile !is VirtualFileWindow) return false

        return vFile.delegate.isKotlinNotebook
    }

fun isKotlinKernelName(kernelName: String?): Boolean {
    return kernelName?.toLowerCaseAsciiOnly() == DEFAULT_KOTLIN_KERNEL_NAME
}

fun JupyterNotebookSession.isKotlinNotebookSession(): Boolean {
    return isKotlinKernelName(kernelName)
}

private val VirtualFile.notebookLanguage: Language?
    get() {
        takeIfBacked(this)?.let {
            return it.notebook.language
        }
        val cachedLanguage = NotebookMetadataLanguageProvider.Utils.getNotebookLanguage(this)
        if (cachedLanguage != null)
            return cachedLanguage

        val calculatedLanguage = getLanguageFromOriginalFile(this)
        if (calculatedLanguage != null) {
            NotebookMetadataLanguageProvider.Utils.setNotebookLanguage(this, calculatedLanguage)
        }

        return calculatedLanguage
    }

private fun getLanguageFromOriginalFile(file: VirtualFile): Language? {
    return try {
        JupyterNotebookBase.createJupyterNotebook(file.inputStream.reader())?.language
    } catch (e: Exception) {
        null
    }
}

fun PsiFile.getTopLevelFile(): PsiFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(this) ?: this

fun VirtualFile.getTopLevelFile(): VirtualFile {
    return if (this is VirtualFileWindow) {
        delegate
    } else {
        this
    }
}

fun PsiFile?.getInjectedKtFiles(): List<KtFile> {
    if (this == null) return emptyList()

    val manager = InjectedLanguageManager.getInstance(project)
    val cells = getNotebookValidCells()

    return buildList {
        for (cell in cells) {
            addAll(
                cell.getInjectedKtFiles(manager)
            )
        }
    }
}

fun PsiFile?.getNotebookValidCells() = getNotebookCells().filter { it.isValid && it is PsiLanguageInjectionHost }

fun PsiFile?.getNotebookCells() =
    (this?.children?.first() as? JupyterNotebook)?.psiCellList.orEmpty()

fun PsiLanguageInjectionHost.getInjectedKtFiles(injectedLanguageManager: InjectedLanguageManager) =
    injectedLanguageManager.getInjectedPsiFiles(this)?.map { it.first }?.filterIsInstance<KtFile>().orEmpty()

fun PsiLanguageInjectionHost.getKtFileStartOffset(injectedLanguageManager: InjectedLanguageManager): Int? {
    val ktFile = getInjectedKtFiles(injectedLanguageManager).firstOrNull() ?: return null
    return injectedLanguageManager.injectedToHost(ktFile, 0)
}

fun VirtualFile.toBackedNotebookFile(): BackedNotebookVirtualFile? =
    takeIfBacked(this) ?: find(this)

fun VirtualFile.findEditor(project: Project): Editor =
    FileEditorManager.getInstance(project).getEditors(this).map { it as TextEditor }.map { it.editor }.first()

@RequiresReadLock
internal fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)

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

internal inline fun <T> withReadAccess(crossinline block: () -> T): T {
    return if (ApplicationManager.getApplication().isDispatchThread) {
        block()
    } else runBlockingMaybeCancellable {
        readAction {
            block()
        }
    }
}

internal fun PsiFile.restartAnalyzing() {
    DaemonCodeAnalyzer.getInstance(this.project).restart(this)
}

suspend fun anyOf(vararg actions: suspend () -> Boolean): Boolean {
    var result = false
    for (action in actions) {
        if (action()) {
            result = true
        }
    }
    return result
}

fun <R> runSafely(action: () -> R, onFailure: (Throwable) -> Unit): R? {
    return try {
        action()
    } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
            throw e
        }
        onFailure(e)
        null
    }
}
