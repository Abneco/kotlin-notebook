// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.helper.NotebookLanguageMatcher
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

const val DEFAULT_KOTLIN_KERNEL_NAME: String = "kotlin"

private val kotlinMatcher by lazy {
    NotebookLanguageMatcher(KotlinLanguage.INSTANCE)
}

val VirtualFile?.isKotlinNotebook: Boolean get() = kotlinMatcher.matches(this)
val BackedNotebookVirtualFile.isKotlinNotebook: Boolean get() = kotlinMatcher.matches(this)
val Editor.isKotlinNotebook: Boolean get() = kotlinMatcher.matches(this)
val JupyterNotebook.isKotlinNotebook: Boolean get() = kotlinMatcher.matches(this)

val KtFile.isInsideKotlinNotebook: Boolean
    get() {
        val vFile = virtualFile ?: return false
        if (vFile !is VirtualFileWindow) return false

        return vFile.delegate.isKotlinNotebook
    }

internal fun PsiElement?.isInsideKotlinNotebookFile(): Boolean {
    val virtualFile = when (val containingFile = this?.containingFile?.virtualFile) {
        is VirtualFileWindow -> containingFile.delegate
        is VirtualFile -> containingFile
        else -> null
    } ?: return false
    return virtualFile.isKotlinNotebook
}

val PsiElement.isInsideKotlinNotebookCodeCell: Boolean get() = containingFile.isKotlinNotebookCodeCell

/**
 * Returns true only for [KtFile]s injected into Kotlin notebooks
 */
val PsiElement.isKotlinNotebookCodeCell: Boolean get() {
    if (this !is KtFile || !isScript()) return false

    val myVirtualFile = virtualFile ?: originalFile.virtualFile
    return (myVirtualFile == null || myVirtualFile is VirtualFileWindow || myVirtualFile is LightVirtualFile) &&
            name.endsWith(JupyterCompilerService.getInstance(project).fileSuffix)
}

fun isKotlinKernelName(kernelName: String?): Boolean {
    return kernelName?.toLowerCaseAsciiOnly() == DEFAULT_KOTLIN_KERNEL_NAME
}

fun JupyterNotebookSession.isKotlinNotebookSession(): Boolean {
    return isKotlinKernelName(kernelName)
}
