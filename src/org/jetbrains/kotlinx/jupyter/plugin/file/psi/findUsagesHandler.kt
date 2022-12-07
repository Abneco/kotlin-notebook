// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.ReadActionProcessor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookGotoDeclarationProvider.Companion.tryGetPreviousValidResolvedResult
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.tryResolveCompiledDeclaration
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile

internal fun isCompiledCellClassDeclaration(element: PsiElement?): Boolean =
    element?.containingFile?.virtualFile?.name?.matches(Regex("Line_.+\\.class")) == true

internal fun isFromJVMDeclaration(element: PsiElement?): Boolean = element?.containingFile?.virtualFile?.name?.endsWith(".class") == true


internal class NotebookFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        val fileWindow = element.containingFile?.virtualFile as? VirtualFileWindow ?:
                        return isCompiledCellClassDeclaration(element) || isFromJVMDeclaration(element)

        val notebookFile = fileWindow.delegate
        val isProperNotebook = BackedNotebookVirtualFile.isBacked(notebookFile) && notebookFile.isKotlinNotebook
        return isProperNotebook && PsiTreeUtil.getParentOfType(element, KtReferenceExpression::class.java) == null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        return KotlinNotebookElementFindUsagesHandler(element, isCompiledCellClassDeclaration(element), isFromJVMDeclaration(element))
    }

}

internal fun tryResolveCompiledDeclarationInNotebook(element: PsiElement, scope: JupyterFile): PsiElement? {
    if (!isCompiledCellClassDeclaration(element)) return null

    tryGetPreviousValidResolvedResult(element)?.let { return it }
    var ans: PsiElement? = null
    runReadAction {
        scope.getNotebookCellList()?.let { targets ->
            ans = tryResolveCompiledDeclaration(element, targets)
            if (ans != null) {
                element.putUserData(IN_EDITOR_ELEM_REF_KEY, ans)
            }
        }
    }
    return ans
}


internal class KotlinNotebookElementFindUsagesHandler(
    element: PsiElement,
    searchWithAdditionalCellDeclarationResolve: Boolean = false,
    isJVMCompliedDeclaration: Boolean = false
) : FindUsagesHandler(element) {
    private var notebookFile = (element.containingFile?.virtualFile as? VirtualFileWindow)?.delegate
    private val targetElementInfo = TargetElementInfo(element, searchWithAdditionalCellDeclarationResolve,
                                                      !searchWithAdditionalCellDeclarationResolve && isJVMCompliedDeclaration)

    override fun getPrimaryElements(): Array<PsiElement> {
        //if (!isBackedNotebook(notebookFile) || !notebookFile.isKotlinNotebook) return emptyArray()
        return arrayOf(super.myPsiElement.navigationElement)
    }

    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): MutableCollection<PsiReference> {
        val time = System.currentTimeMillis()
        val foundRefs = NotebookUsagesContributorFactory
            .invokeElementUsagesContributor(targetElementInfo, searchScope)

        //println("Found refs of size: ${foundRefs?.size} in ${System.currentTimeMillis() - time} ms")
        return foundRefs?.map {// mapTo ?
            val properFileRange = ensureProperTextRangeShiftInFile(it)
            NotebookReferenceWrapper(target, it, properFileRange, true)
        }?.toMutableSet() ?: mutableSetOf()
    }

    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        val refProcessor: ReadActionProcessor<PsiReference> = object : ReadActionProcessor<PsiReference>() {
            override fun processInReadAction(ref: PsiReference): Boolean {
                return processor.process(UsageInfo(ref.element, ref.rangeInElement, false))
            }
        }
        var result = true
        runReadAction {
            val foundUsages = findUsageForElement(element)
            if (foundUsages.isNullOrEmpty()) result = false

            foundUsages?.iterator()?.forEach {
                val properFileRange = ensureProperTextRangeShiftInFile(it)
                refProcessor.processInReadAction(NotebookReferenceWrapper(super.myPsiElement, it, properFileRange, true))
            }
        }

        return result
    }

    private fun ensureProperTextRangeShiftInFile(usage: PsiElement): TextRange {
        //if (target.containingFile == usage.containingFile) return usage.textRange
        val fileRange = usage.containingFile.textRange
        val rangeToStore = TextRange.create(usage.textRangeInParent.startOffset, usage.textRangeInParent.endOffset).shiftRight(usage.textRange.startOffset)

        val lDiff = if (rangeToStore.startOffset > fileRange.endOffset) rangeToStore.startOffset - fileRange.endOffset else 0
        val rDiff = if (rangeToStore.endOffset > fileRange.endOffset) rangeToStore.endOffset - fileRange.endOffset else 0
        val maxDiff = maxOf(lDiff, rDiff)
        val firstChild = usage.firstChild
        if (usage is PsiNameIdentifierOwner) {
            return usage.nameIdentifier?.textRangeInParent ?: usage.textRangeInParent
        }
        if (maxDiff != 0) {
            return usage.textRangeInParent.shiftLeft(usage.textRangeInParent.startOffset)
        }
        return if (firstChild?.isIdentifier() == true) firstChild.textRangeInParent else usage.textRangeInParent
    }

    private fun findUsageForElement(targetElement: PsiElement): Set<PsiElement>? {
        val scriptingSupport = JupyterKtScriptingSupport.getInstance(targetElement.project)
        val notebookFileState = notebookFile ?: return null
        return scriptingSupport.searchForElementDeclarationOrUsages(adjustElement(targetElement), notebookFileState, searchStrategy = ReferenceSearchStrategy.REFERENCES)
    }

}

internal fun adjustElement(psiElement: PsiElement): PsiElement {
    var curElement: PsiElement? = null
    if (psiElement is KtPrimaryConstructor || psiElement is KtObjectDeclaration) {
        return psiElement.parentOfType<KtClass>()?.nameIdentifier ?: psiElement
    }
    if (psiElement is PsiNameIdentifierOwner) {
        return psiElement.nameIdentifier ?: psiElement
    }
    if (psiElement !is PsiIdentifier) {
        psiElement.accept(object: PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (curElement == null && element.isIdentifier()) curElement = element
                super.visitElement(element)
            }
        })
    }
    return curElement ?: psiElement
}

// maybe would be needed
internal class KotlinNotebookElementManipulator: AbstractElementManipulator<KtReferenceExpression?>() {
    override fun handleContentChange(element: KtReferenceExpression, range: TextRange, newContent: String): KtReferenceExpression? {
        val text = element.text
        val content = text.replaceRange(range.startOffset, range.endOffset, newContent)
        return ((element.firstChild as? LeafPsiElement)?.replaceWithText(content) as? LeafPsiElement)?.parent as? KtReferenceExpression
    }
}


internal class LeafElementManipulator: AbstractElementManipulator<LeafPsiElement>() {
    override fun handleContentChange(element: LeafPsiElement, range: TextRange, newContent: String): LeafPsiElement? {
        val text = element.text
        val content = text.replaceRange(range.startOffset, range.endOffset, newContent)
        return element.replaceWithText(content) as? LeafPsiElement
    }
}

internal fun PsiElement?.isIdentifier(): Boolean = elementType?.debugName?.equals("IDENTIFIER") == true