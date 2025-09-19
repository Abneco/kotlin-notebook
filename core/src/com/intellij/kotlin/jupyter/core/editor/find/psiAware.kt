// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.find

import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructurePerFileTracker.Companion.CELL_CLASS_NAME
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructureTrackerService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.util.runIf
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript


fun searchForElementDeclarationOrUsages(
    project: Project,
    target: PsiElement,
    virtualFile: VirtualFile,
    searchStrategy: ReferenceSearchStrategy
): MutableSet<PsiElement>? {
    if (!virtualFile.isKotlinNotebook) return null
    val injectedManager = InjectedLanguageManager.getInstance(project)
    val foundData = mutableSetOf<PsiElement>()
    val asPsiFile = virtualFile.findPsiFile(project) ?: return null
    val notebookCells = asPsiFile.getNotebookCells().ifEmpty { return null }
    val backedNotebookVirtualFile = virtualFile.toBackedNotebookFile()

    val notebookStructureTracker = NotebookStructureTrackerService.getForFile(project, backedNotebookVirtualFile)
    val ordinalMap = notebookStructureTracker.cellOrdinalToCompiledlassNames

    val injectionManager = InjectedLanguageManager.getInstance(project)
    val targetHost = injectionManager.getInjectionHost(target.containingFile)
    val targetClassName = runIf(searchStrategy == ReferenceSearchStrategy.REFERENCES && targetHost != null) {
        val name = ordinalMap[notebookCells.indexOf(targetHost!!)]
        if (targetHost.getUserData(CELL_CLASS_NAME) == null && name != null) targetHost.putUserData(CELL_CLASS_NAME, name)
        name
    }
    if (target.parent == null) return null // means we have inconsistent notebook state
    val targetContainingFile = target.containingFile

    var isLocalSearch = if (searchStrategy == ReferenceSearchStrategy.REFERENCES) {
        targetHost?.getUserData(CELL_CLASS_NAME) == null && !isCompiledCellClassDeclaration(target)
    } else false
    if (!isLocalSearch && targetContainingFile.name.contains(NotebookUsagesContributorFactory.DATAFRAME_PREFIX)) {
        isLocalSearch = isItGeneratedNameInsideLambdaCall(target, target)
    }
    // println("isLocalSearch: $isLocalSearch for ${target.text}")
    val scopeContainers = if (isLocalSearch) listOf(injectionManager.getInjectionHost(targetContainingFile)) else notebookCells

    return runReadAction {
        for (ind in scopeContainers.indices) {
            val gotHost = scopeContainers[ind] ?: continue
            val host = if (isLocalSearch) gotHost else notebookCells[ind]
            val firstInjectedFileInfo = injectedManager.getInjectedPsiFiles(host)?.firstOrNull() ?: continue
            val psiFile = firstInjectedFileInfo.first ?: continue
            // should second part still be there?
            if (target.parent == null) { // means we have inconsistent notebook state
                break
            }
            if (psiFile !is KtFile || (psiFile == targetContainingFile && searchStrategy == ReferenceSearchStrategy.DECLARATION)) continue
            val scriptBlock = psiFile.findChildrenByClass(KtScript::class.java).firstOrNull()?.blockExpression ?: continue
            val elements = mutableListOf<NavigatablePsiElement>()
            val possibleClassName = ordinalMap[ind].orEmpty()
            NotebookReferenceFinder.traverseChildrenAndSearch(
                injectionManager, host, targetClassName ?: possibleClassName, scriptBlock, target, searchStrategy,
                elements
            )

            if (searchStrategy == ReferenceSearchStrategy.DECLARATION) {
                val first = elements.firstOrNull()
                if (first != null) {
                    foundData.add(first)
                    break
                }
            } else foundData += elements
        }

        return@runReadAction foundData
    }
}