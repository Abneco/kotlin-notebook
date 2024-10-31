// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile.Companion.find
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.helper.notebookLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

const val JUPYTER_NOTEBOOK_EXTENSION = "ipynb"
const val DEFAULT_KOTLIN_KERNEL_NAME = "kotlin"

val VirtualFile?.isKotlinNotebook: Boolean
    get() {
        if (this == null || extension != JUPYTER_NOTEBOOK_EXTENSION) return false
        return notebookLanguage === KotlinLanguage.INSTANCE
    }

val BackedNotebookVirtualFile.isKotlinNotebook: Boolean
    get() {
        return notebook.language === KotlinLanguage.INSTANCE
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

@RequiresReadLock
fun VirtualFile.toPsiFile(project: Project): PsiFile? =
    PsiManager.getInstance(project).findFile(this)

internal fun PsiFile.toDocument(): Document? =
    PsiDocumentManager.getInstance(this.project).getDocument(this)

internal fun PsiElement?.isInsideKotlinNotebookFile(): Boolean {
    val virtualFile = when (val containingFile = this?.containingFile?.virtualFile) {
        is VirtualFileWindow -> containingFile.delegate
        is VirtualFile -> containingFile
        else -> null
    } ?: return false
    return virtualFile.isKotlinNotebook
}

internal fun retrieveElementUnderCaret(scope: PsiFile): PsiElement? {
    val editor = scope.project.getCurrentEditorOrNull() ?: return null
    val caretOffSet = editor.caretModel.offset
    val injectedManager = InjectedLanguageManager.getInstance(scope.project)
    val host = scope.findElementAt(caretOffSet)?.parentOfType<JupyterPsiCell>() as? PsiLanguageInjectionHost ?: return null
    val injectInfo = injectedManager.getInjectedPsiFiles(host)?.firstOrNull()?.first ?: return null

    return (injectInfo as? PsiFile)?.findElementAt(
        (caretOffSet - host.startOffsetInParent - 5).coerceAtLeast(0)
    )
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

suspend inline fun anyOf(vararg actions: suspend () -> Boolean): Boolean {
    var result = false
    for (action in actions) {
        if (action()) {
            result = true
        }
    }
    return result
}

@OptIn(ExperimentalContracts::class)
inline fun <R> runSafely(action: () -> R, onFailure: (Throwable) -> Unit): R? {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
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

inline fun <R> runSafelyTyped(crossinline action: () -> R, crossinline onFailure: (Throwable) -> R): R {
    return try {
        action()
    } catch (e: Throwable) {
        if (e is ProcessCanceledException) {
            throw e
        }
        onFailure(e)
    }
}
