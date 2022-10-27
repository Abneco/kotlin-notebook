// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlinx.jupyter.plugin.file.isInsideKotlinNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile


sealed class NotebookUsagesContributor {
    protected fun findUsageForElement(scope: VirtualFile, targetElement: PsiElement): Array<PsiElement>? {
        val scriptingSupport = JupyterKtScriptingSupport.getInstance(targetElement.project)
        return scriptingSupport.searchForElementDeclarationOrUsages(adjustElement(targetElement), scope, searchStrategy = ReferenceSearchStrategy.REFERENCES)
    }

    protected fun searchInSourcesScope(scope: VirtualFile, targetElement: PsiElement): Array<PsiElement>?  {
        if (!targetElement.isInsideKotlinNotebookFile()) return null
        return findUsageForElement(scope, targetElement)
    }

    protected fun searchWithCompiledCellScope(scope: VirtualFile, targetElement: PsiElement): Array<PsiElement>? {
        var adjustedElement = targetElement
        val asPsiFile = scope.toPsiFile(targetElement.project) as? JupyterFile ?: return null
        tryResolveCompiledDeclarationInNotebook(targetElement, asPsiFile)?.let {
            adjustedElement = it
        }

        return findUsageForElement(scope, adjustedElement)
    }


    protected fun searchProvidedLibrariesUsagesInNotebook(scope: VirtualFile, targetElement: PsiElement): Array<PsiElement>? {
        val asPsiFile = scope.toPsiFile(targetElement.project) as? JupyterFile ?: return null
        return null
    }
}

typealias UsageSearchSupplier = (VirtualFile, PsiElement) -> Array<PsiElement>?


@ApiStatus.Experimental
internal object NotebookUsagesContributorFactory : NotebookUsagesContributor() {
    enum class SearchPattern {
        Sources, CompiledCellClass, ProvidedLibraries
    }

    private val searchPatternSolutions = mapOf<SearchPattern, UsageSearchSupplier>(
        SearchPattern.Sources to ::searchInSourcesScope,
        SearchPattern.CompiledCellClass to ::searchWithCompiledCellScope
    )

    private const val dfPrefix = ".kotlinx.dataframe."
    private fun isDataFrameLib(element: PsiElement): Boolean = element.containingFile.name.contains(dfPrefix)

    private fun extractNotebookFileFromScope(element: PsiElement, scope: SearchScope): VirtualFile? {
        return when (val scopeFile = (scope as? LocalSearchScope)?.virtualFiles?.firstOrNull()) {
            is VirtualFileWindow -> scopeFile.delegate
            else -> scopeFile
        }
    }

    fun invokeElementUsagesContributor(element: PsiElement, scope: SearchScope, isFromCompiledCellClass: Boolean): Array<PsiElement>? {
        val notebookFile = extractNotebookFileFromScope(element, scope) ?: return null
        if (!isBackedNotebook(notebookFile) || !notebookFile.isKotlinNotebook) return null

        val properKey = if (isFromCompiledCellClass) SearchPattern.CompiledCellClass
                        else if (isDataFrameLib(element)) SearchPattern.ProvidedLibraries else SearchPattern.Sources
        return searchPatternSolutions[properKey]?.invoke(notebookFile, element)
    }
}

