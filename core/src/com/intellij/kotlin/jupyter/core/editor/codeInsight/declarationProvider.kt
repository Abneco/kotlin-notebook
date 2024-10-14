// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.find.IN_EDITOR_ELEM_REF_KEY
import com.intellij.kotlin.jupyter.core.editor.find.NotebookReferenceFinder.CELL_CLASS_NAME
import com.intellij.kotlin.jupyter.core.editor.find.ReferenceSearchStrategy
import com.intellij.kotlin.jupyter.core.editor.find.searchForElementDeclarationOrUsages
import com.intellij.kotlin.jupyter.core.editor.find.tryResolveCompiledDeclarationInNotebook
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toPsiFile
import com.intellij.model.psi.PsiSymbolService
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferenceSearcher
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Query
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile


class NotebookGotoDeclarationProvider: GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        sourceElement ?: return null

        val project = sourceElement.project
        val psiFile = sourceElement.containingFile
        val virtualFile = psiFile.virtualFile as? VirtualFileWindow ?: return emptyArray()
        val notebookFile = virtualFile.delegate
        if (!BackedNotebookVirtualFile.isBacked(notebookFile)) return emptyArray()
        sourceElement.reference?.resolve()?.let { return arrayOf(it) }
        val refExpr = sourceElement.getParentOfType<KtReferenceExpression>(true) ?: return emptyArray()
        if (refExpr.references.none { it.resolve() != null } )  return emptyArray()
        tryGetPreviousValidResolvedResult(sourceElement)?.let { return arrayOf(it) }

        if (PsiTreeUtil.getParentOfType(sourceElement, KtReferenceExpression::class.java) == null) {
            return null
        }
        // try fast
        val targetSymbol = targetSymbols(psiFile, offset).firstOrNull()
        val adjustedElement = if (targetSymbol != null) PsiSymbolService.getInstance().extractElementFromSymbol(targetSymbol) ?: sourceElement else sourceElement
        (notebookFile.toPsiFile(project) as? JupyterFile)?.let {
            tryResolveCompiledDeclarationInNotebook(adjustedElement, it)?.let { foundDeclaration ->
                sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, foundDeclaration)
                return arrayOf(foundDeclaration)
            }
        }

        // try exhaustive search
        return searchForElementDeclarationOrUsages(
            project, sourceElement, notebookFile,
            ReferenceSearchStrategy.DECLARATION
        )?.firstOrNull()?.let {
            sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, it)
            arrayOf(it)
        } ?: emptyArray()
    }

    companion object {
        internal fun tryGetPreviousValidResolvedResult(sourceElement: PsiElement): PsiElement? {
            sourceElement.getUserData(IN_EDITOR_ELEM_REF_KEY)?.let {
                val knownRef = sourceElement.parent?.reference?.resolve()
                if (!it.isValid || !it.containingFile.isValid) {
                    sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, null)
                    return null
                }
                val storedClassName = it.containingFile.getUserData(CELL_CLASS_NAME)
                if (knownRef != null && it.isValid && storedClassName?.contains(knownRef.containingFile.name.substringBefore(".class")) == true
                    || storedClassName?.contains(sourceElement.containingFile.name.substringBefore(".class")) == true) {
                    if (it.containingFile.isValid) {
                        return it
                    }
                }
                sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, null)
            }
            return null
        }
    }

}


internal class NotebookReferencesProvider: ReferenceSearcher {
    override fun collectSearchRequests(parameters: ReferencesSearch.SearchParameters): Collection<Query<out PsiReference>> {
        val virtualFile = parameters.elementToSearch.containingFile?.virtualFile ?: return emptyList()
        if (virtualFile !is VirtualFileWindow) return emptyList()
        val backedNotebook = BackedNotebookVirtualFile.takeIfBacked(virtualFile)

        if (backedNotebook == null || !virtualFile.isKotlinNotebook) return emptyList()
        if (!parameters.scopeDeterminedByUser.contains(backedNotebook.originFile)) return emptyList()
        if (parameters.effectiveSearchScope is LocalSearchScope) return emptyList()

        return listOf(
            ReferencesSearch.search(
                ReferencesSearch.SearchParameters(
                    parameters.elementToSearch,
                    LocalSearchScope(parameters.elementToSearch.containingFile),
                    false
                )))
    }
}