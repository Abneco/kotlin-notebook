// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.find

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.runIf
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.NotebookStructureTrackerService
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook


fun searchForElementDeclarationOrUsages(
    project: Project,
    target: PsiElement,
    virtualFile: VirtualFile,
    searchStrategy: ReferenceSearchStrategy
): MutableSet<PsiElement>? {
    if (!virtualFile.isKotlinNotebook) return null
    val injectedManager = InjectedLanguageManager.getInstance(project)
    val foundData = mutableSetOf<PsiElement>()
    val asPsiFile = PsiManager.getInstance(project).findFile(virtualFile)
    val notebookCells = (asPsiFile?.children?.first() as? JupyterNotebook)?.psiCellList ?: return null
    val ordinalMap = NotebookStructureTrackerService.getForFile(project, BackedNotebookVirtualFile(virtualFile)).cellOrdinalToClassNameStructure
    val injectionManager = InjectedLanguageManager.getInstance(project)
    val targetHost = injectionManager.getInjectionHost(target.containingFile)
    val targetClassName = runIf(searchStrategy == ReferenceSearchStrategy.REFERENCES) {
        targetHost?.let {
            val name = ordinalMap[notebookCells.indexOf(it)]
            if (it.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME) == null && name != null) it.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, name)
            name
        }
    }
    if (target.parent == null) return null // means we have inconsistent notebook state
    val targetContainingFile = target.containingFile

    var isLocalSearch = if (searchStrategy == ReferenceSearchStrategy.REFERENCES) {
        targetHost?.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME) == null && !isCompiledCellClassDeclaration(target)
    } else false
    if (!isLocalSearch && targetContainingFile.name.contains(NotebookUsagesContributorFactory.DATAFRAME_PREFIX)) {
        isLocalSearch = isItGeneratedNameInsideLambdaCall(target, target)
    }
    // println("isLocalSearch: $isLocalSearch for ${target.text}")
    val properContainer = if (isLocalSearch) listOf(injectionManager.getInjectionHost(targetContainingFile)) else notebookCells

    return runReadAction {
        for (ind in properContainer.indices) {
            val gotHost = properContainer[ind] ?: continue
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
            val possibleClassName = ordinalMap[ind]
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