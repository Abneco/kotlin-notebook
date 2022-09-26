// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferenceSearcher
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Query
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.CELL_CLASS_NAME
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.originFile


class NotebookGotoDeclarationProvider: GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        sourceElement ?: return null

        val project = sourceElement.project
        val psiFile = sourceElement.containingFile
        val virtualFile = psiFile.virtualFile as? VirtualFileWindow ?: return emptyArray()
        val notebookFile = virtualFile.delegate
        if (!isBackedNotebook(notebookFile)) return emptyArray()
        sourceElement.reference?.resolve()?.let { return arrayOf(it) }
        tryGetPreviousValidResolvedResult(sourceElement)?.let { return arrayOf(it) }
        val scriptingSupport = JupyterKtScriptingSupport.getInstance(project)


        if (PsiTreeUtil.getParentOfType(sourceElement, KtReferenceExpression::class.java) == null) {
            return null
        }

        return scriptingSupport.searchForElementDeclarationOrUsages(sourceElement, notebookFile, ReferenceSearchStrategy.DECLARATION)
            ?.firstOrNull()?.let {
                sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, it)
                arrayOf(it)
            } ?: emptyArray()
    }

    private fun tryGetPreviousValidResolvedResult(sourceElement: PsiElement): PsiElement? {
        sourceElement.getUserData(IN_EDITOR_ELEM_REF_KEY)?.let {
            val knownRef = sourceElement.parent?.reference?.resolve()
            if (knownRef != null && it.isValid && (it.containingFile.getUserData(CELL_CLASS_NAME) + ".class") == knownRef.containingFile.name) {
                if (it.containingFile.isValid) {
                    return it
                }
            }
            sourceElement.putUserData(IN_EDITOR_ELEM_REF_KEY, null)
        }
        return null
    }

}


internal class NotebookReferencesProvider: ReferenceSearcher {
    override fun collectSearchRequests(parameters: ReferencesSearch.SearchParameters): Collection<Query<out PsiReference>> {
        val virtualFile = parameters.elementToSearch.containingFile?.virtualFile ?: return emptyList()
        if (virtualFile !is VirtualFileWindow) return emptyList()
        if (!isBackedNotebook(virtualFile) || !virtualFile.isKotlinNotebook) return emptyList()

        if (!parameters.scopeDeterminedByUser.contains(virtualFile.originFile)) return emptyList()
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