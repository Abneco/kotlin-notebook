// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ReadActionProcessor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.isBackedNotebook

internal class NotebookFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        val fileWindow = element.containingFile?.virtualFile as? VirtualFileWindow ?: return false
        val notebookFile = fileWindow.delegate
        val isProperNotebook = isBackedNotebook(notebookFile) && notebookFile.isKotlinNotebook
        return isProperNotebook && PsiTreeUtil.getParentOfType(element, KtReferenceExpression::class.java) == null
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler? {
        return KotlinNotebookElementFindUsagesHandler(element)
    }

}

internal class KotlinNotebookElementFindUsagesHandler(element: PsiElement) : FindUsagesHandler(element) {
    private val notebookFile = (element.containingFile?.virtualFile as? VirtualFileWindow)?.delegate

    override fun getPrimaryElements(): Array<PsiElement> {
        if (notebookFile !is BackedVirtualFile || !notebookFile.isKotlinNotebook) return emptyArray()
        return arrayOf(super.myPsiElement.navigationElement)
    }

    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): MutableCollection<PsiReference> {
        val virtualFile = (target.containingFile?.virtualFile as? VirtualFileWindow)?.delegate ?: return mutableSetOf()
        if (virtualFile !is BackedVirtualFile || !virtualFile.isKotlinNotebook) return mutableSetOf()

        return findUsageForElement(target)?.map {
            val properFileRange = ensureProperTextRangeShiftInFile(it)
            //val properRange = if (!it.textRangeInParent.containsRange(fileRange.startOffset, fileRange.endOffset)) it.textRangeInParent.shiftLeft(1) else it.textRangeInParent
            NotebookReferenceWrapper(target, it, properFileRange, true)
        }?.toMutableSet() ?: mutableSetOf()
    }

    override fun processElementUsages(element: PsiElement, processor: Processor<in UsageInfo>, options: FindUsagesOptions): Boolean {
        val refProcessor: ReadActionProcessor<PsiReference> = object : ReadActionProcessor<PsiReference>() {
            override fun processInReadAction(ref: PsiReference): Boolean {
                return processor.process(UsageInfo(ref))
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
        val fileRange = usage.containingFile.textRange
        val rangeToStore = TextRange.create(usage.textRangeInParent.startOffset, usage.textRangeInParent.endOffset).shiftRight(usage.textRange.startOffset)

        val lDiff = if (rangeToStore.startOffset > fileRange.endOffset) rangeToStore.startOffset - fileRange.endOffset else 0
        val rDiff = if (rangeToStore.endOffset > fileRange.endOffset) rangeToStore.endOffset - fileRange.endOffset else 0
        val maxDiff = maxOf(lDiff, rDiff)

        if (maxDiff != 0) {
            return usage.textRangeInParent.shiftLeft(usage.textRangeInParent.startOffset)
        }
        return usage.textRangeInParent
    }

    private fun findUsageForElement(targetElement: PsiElement): Array<PsiElement>? {
        val scriptingSupport = JupyterKtScriptingSupport.getInstance(targetElement.project)
        notebookFile ?: return null
        return scriptingSupport.searchForElementDeclarationOrUsages(adjustElement(targetElement), notebookFile, searchStrategy = ReferenceSearchStrategy.REFERENCES)
    }

    private fun adjustElement(psiElement: PsiElement): PsiElement {
        var curElement: PsiElement? = null
        if (psiElement is KtFunction) {
            return psiElement.nameIdentifier ?: psiElement
        }
        if (psiElement !is PsiIdentifier) {
            psiElement.accept(object: PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (curElement == null && element.elementType?.debugName == "IDENTIFIER") curElement = element
                    super.visitElement(element)
                }
            })
        }
        return curElement ?: psiElement
    }
}

// maybe would be needed
internal class KotlinNotebookElementManipulator: ElementManipulator<KtReferenceExpression> {
    override fun handleContentChange(element: KtReferenceExpression, range: TextRange, newContent: String?): KtReferenceExpression? {
        return element
    }

    override fun handleContentChange(element: KtReferenceExpression, newContent: String?): KtReferenceExpression? {
        return element
    }

    override fun getRangeInElement(element: KtReferenceExpression): TextRange {
        return element.textRange
    }
}