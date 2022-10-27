// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.SingleTargetRequestResultProcessor
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlinx.jupyter.plugin.file.isInsideKotlinNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile


internal class ProvidedLibrariesReferencesProducer: Processor<PsiReference> {
    val foundRefs = mutableSetOf<PsiReference>()

    override fun process(t: PsiReference?): Boolean {
        t?.let {
            foundRefs.add(t)
        }
        return true
    }
}

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

    // maybe don't needed
    protected fun searchProvidedLibrariesUsagesInNotebook(scope: VirtualFile, targetElement: PsiElement): Array<PsiElement>? {
        val asPsiFile = scope.toPsiFile(targetElement.project) as? JupyterFile ?: return null
        val singleTargetRequestResultProcessor = SingleTargetRequestResultProcessor(targetElement)
        val refsProcessor = ProvidedLibrariesReferencesProducer()
        val processor = TextOccurenceProcessor { element: PsiElement?, offsetInElement: Int ->
                singleTargetRequestResultProcessor.processTextOccurrence(element!!, offsetInElement, refsProcessor)
            }
        val project = targetElement.project

        val helper = PsiSearchHelper.getInstance(project)
        val goalText = adjustElement(targetElement).text
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val files = injectedManager.getInjectedPsiFiles(asPsiFile)?.filter { !it.first.containingFile.name.endsWith("jkmt") }?.map { it.first }
        if (files.isNullOrEmpty()) return null

        val currentLocalSearchScope = LocalSearchScope(files.toTypedArray())

        helper.processElementsWithWord(processor, currentLocalSearchScope, goalText, UsageSearchContext.ANY,  true, false)
        return refsProcessor.foundRefs.mapNotNull { it.resolve() }.toTypedArray()
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
        SearchPattern.CompiledCellClass to ::searchWithCompiledCellScope,
        SearchPattern.ProvidedLibraries to ::searchProvidedLibrariesUsagesInNotebook
    )

    const val dfPrefix = ".kotlinx.dataframe."
    private fun isDataFrameLib(element: PsiElement): Boolean = element.text.contains(dfPrefix) //&& element.ownDeclarations.isEmpty()

    private fun extractNotebookFileFromScope(element: PsiElement, scope: SearchScope): VirtualFile? {
        return when (val scopeFile = (scope as? LocalSearchScope)?.virtualFiles?.firstOrNull()) {
            is VirtualFileWindow -> scopeFile.delegate
            else -> scopeFile
        }
    }

    fun invokeElementUsagesContributor(element: PsiElement, scope: SearchScope, isFromCompiledCellClass: Boolean): Array<PsiElement>? {
        val notebookFile = extractNotebookFileFromScope(element, scope) ?: return null
        if (!isBackedNotebook(notebookFile) || !notebookFile.isKotlinNotebook) return null


        //// order is important
        //val properKey = if (isDataFrameLib(element)) SearchPattern.ProvidedLibraries
        //else if (isFromCompiledCellClass) SearchPattern.CompiledCellClass
        //else SearchPattern.Sources
        val properKey = if (isFromCompiledCellClass) SearchPattern.CompiledCellClass
                        else if (isDataFrameLib(element)) SearchPattern.ProvidedLibraries else SearchPattern.Sources
        return searchPatternSolutions[properKey]?.invoke(notebookFile, element)
    }
}

