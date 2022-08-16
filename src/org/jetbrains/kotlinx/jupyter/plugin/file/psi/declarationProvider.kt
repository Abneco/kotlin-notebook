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
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.originFile


class NotebookGotoDeclarationProvider: GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        sourceElement ?: return null

        val project = sourceElement.project
        val file = sourceElement.containingFile
        if (file.virtualFile !is VirtualFileWindow) return emptyArray()
        val notebookFile = (file.virtualFile as VirtualFileWindow).delegate
        if (!isBackedNotebook(notebookFile)) return emptyArray()
        sourceElement.reference?.resolve()?.let { return arrayOf(it) }
        val scriptingSupport = JupyterKtScriptingSupport.getInstance(project)


        val searchStrategy = if (PsiTreeUtil.getParentOfType(sourceElement, KtReferenceExpression::class.java) != null) {
            SearchStrategy.DECLARATION
        } else SearchStrategy.REFERENCES
        if (searchStrategy == SearchStrategy.REFERENCES) return null

        return scriptingSupport.searchForElementDeclarationOrUsages(sourceElement, notebookFile, SearchStrategy.DECLARATION)
            ?.firstOrNull()?.let {
                sourceElement.reference?.bindToElement(it)
                arrayOf(it)
            } ?: emptyArray()
    }

}


internal class NotebookReferencesProvider: ReferenceSearcher {
    override fun collectSearchRequests(parameters: ReferencesSearch.SearchParameters): Collection<Query<out PsiReference>> {
        val virtualFile = parameters.elementToSearch.containingFile?.virtualFile ?: return emptyList()
        if (virtualFile !is VirtualFileWindow) return emptyList()
        val notebookFile = (virtualFile as VirtualFileWindow).delegate
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