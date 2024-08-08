// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.find

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
import com.intellij.psi.util.parentOfType
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.isInsideKotlinNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.retrieveElementUnderCaret
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile

// todo: convert to lambda
internal class ProvidedLibrariesReferencesProducer: Processor<PsiReference> {
    val foundRefs = mutableSetOf<PsiReference>()

    override fun process(t: PsiReference?): Boolean {
        t?.let {
            foundRefs.add(t)
        }
        return true
    }
}

internal typealias TargetElementInfo = Triple<PsiElement, Boolean, Boolean>

sealed class NotebookUsagesContributor {
    private fun findUsageForElement(scope: VirtualFile, targetElement: PsiElement): MutableSet<PsiElement>? {
        return searchForElementDeclarationOrUsages(
            targetElement.project, adjustElement(targetElement), scope,
            searchStrategy = ReferenceSearchStrategy.REFERENCES
        )
    }

    protected fun searchInSourcesScope(scope: VirtualFile, targetElement: PsiElement, @Suppress("UNUSED_PARAMETER") isFromDSLibs: Boolean): Array<PsiElement>? {
        if (!targetElement.isInsideKotlinNotebookFile()) return null
        return findUsageForElement(scope, targetElement)?.toTypedArray()
    }

    protected fun searchWithCompiledCellScope(scope: VirtualFile, targetElement: PsiElement, @Suppress("UNUSED_PARAMETER") isFromDSLibs: Boolean): Array<PsiElement>? {
        var adjustedElement = targetElement
        val asPsiFile = scope.toPsiFile(targetElement.project) as? JupyterFile ?: return null
        tryResolveCompiledDeclarationInNotebook(targetElement, asPsiFile)?.let {
            adjustedElement = it
        }
        // add target here as well
        return findUsageForElement(scope, adjustedElement)?.let {
            val enclosingDeclaration = adjustedElement.parentOfType<KtDeclaration>(withSelf = true) ?: adjustedElement
            if (enclosingDeclaration.containingFile?.name?.endsWith("class") == false) {
                it.add(enclosingDeclaration)
            }
            it.toTypedArray()
        }
    }

    // maybe don't needed
    protected fun searchProvidedLibrariesUsagesInNotebook(scope: VirtualFile, targetElement: PsiElement, isFromDSLibs: Boolean): Array<PsiElement>? {
        val project = targetElement.project
        val asPsiFile = scope.toPsiFile(project) as? JupyterFile ?: return null
        val singleTargetRequestResultProcessor = SingleTargetRequestResultProcessor(targetElement)
        val refsProcessor = ProvidedLibrariesReferencesProducer()
        val processor = TextOccurenceProcessor { element: PsiElement?, offsetInElement: Int ->
                singleTargetRequestResultProcessor.processTextOccurrence(element!!, offsetInElement, refsProcessor)
            }

        val helper = PsiSearchHelper.getInstance(project)
        val goalText = adjustElement(targetElement).text
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val elemUnderCaret = retrieveElementUnderCaret(asPsiFile)

        // || to strict insideLambda rule optimisation
        val files = if (isFromDSLibs || isItGeneratedNameInsideLambdaCall(targetElement, elemUnderCaret)) listOfNotNull(elemUnderCaret?.containingFile)
                    else asPsiFile.getNotebookCells().mapNotNull { injectedManager.getInjectedPsiFiles(it)?.firstOrNull()?.first }
        if (files.isEmpty()) return null

        val currentLocalSearchScope = LocalSearchScope(files.toTypedArray())
        helper.processElementsWithWord(processor, currentLocalSearchScope, goalText, UsageSearchContext.ANY,  true, false)
        return refsProcessor.foundRefs.map { it.element }.toTypedArray()
    }

}

typealias UsageSearchSupplier = (VirtualFile, PsiElement, Boolean) -> Array<PsiElement>?


@ApiStatus.Experimental
internal data object NotebookUsagesContributorFactory : NotebookUsagesContributor() {
    enum class SearchPattern {
        Sources, CompiledCellClass, ProvidedLibrariesOrJVMDeclaration
    }

    private val searchPatternSolutions = mapOf<SearchPattern, UsageSearchSupplier>(
        SearchPattern.Sources to ::searchInSourcesScope,
        SearchPattern.CompiledCellClass to ::searchWithCompiledCellScope,
        SearchPattern.ProvidedLibrariesOrJVMDeclaration to ::searchProvidedLibrariesUsagesInNotebook
    )

    const val DATAFRAME_PREFIX = ".kotlinx.dataframe." // dataFrame
    // search through ".kotlinx.dataframe.^DataFrame" package
    private fun isFromDataFrameLibInternals(element: PsiElement): Boolean = element.text?.let {
        it.contains(DATAFRAME_PREFIX) && !it.contains("${DATAFRAME_PREFIX}DataFrame")
    } ?: false

    private fun extractNotebookFileFromScope(element: PsiElement, scope: SearchScope): VirtualFile? {
        return when (val scopeFile = (scope as? LocalSearchScope)?.virtualFiles?.firstOrNull()) {
            is VirtualFileWindow -> scopeFile.delegate
            else -> if (element.containingFile.virtualFile is VirtualFileWindow)
                (element.containingFile.virtualFile as? VirtualFileWindow)?.delegate else scopeFile
        }
    }

    fun invokeElementUsagesContributor(targetElementInfo: TargetElementInfo, scope: SearchScope): Array<PsiElement>? {
        val (element, isFromCompiledCellClass, isFromByteCode) = targetElementInfo
        val notebookFile = extractNotebookFileFromScope(element, scope) ?: return null
        if (!BackedNotebookVirtualFile.isBacked(notebookFile) || !notebookFile.isKotlinNotebook) return null

        val isFromDSLibs = isFromDataFrameLibInternals(element)
        val properKey = if (isFromDSLibs || isFromByteCode) SearchPattern.ProvidedLibrariesOrJVMDeclaration
                        else if (isFromCompiledCellClass) SearchPattern.CompiledCellClass
                        else SearchPattern.Sources
        //val properKey = if (isFromCompiledCellClass) SearchPattern.CompiledCellClass
        //                else if (isDataFrameLib(element)) SearchPattern.ProvidedLibraries else SearchPattern.Sources
        return searchPatternSolutions[properKey]?.invoke(notebookFile, element, isFromDSLibs)
    }
}

