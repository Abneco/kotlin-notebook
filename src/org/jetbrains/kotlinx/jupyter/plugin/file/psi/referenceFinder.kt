// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType

enum class SearchStrategy {
    DECLARATION,
    REFERENCES
}

object NotebookReferenceFinder {
    // Holds compiled class name
    val CELL_WAS_COMPILED: Key<String> = Key.create("IF_CELL_WAS_COMPILED")

    private val referenceResolver = NotebookReferenceExpressionResolver()

    fun traverseChildrenAndSearch(element: PsiElement, targetElement: PsiElement,
                                  searchStrategy: SearchStrategy = SearchStrategy.DECLARATION): MutableList<out NavigatablePsiElement>? {
        var ans: MutableList<out NavigatablePsiElement>? = null
        val declaredPublicClasses = element.children.filter {
            it is KtClass && it.isPublic
        }
        if (targetElement.reference?.resolve()?.containingFile?.fileType?.defaultExtension == "kt") return null

        val dotExpression = targetElement.getParentOfType<KtDotQualifiedExpression>(false)
        var referenceInfo: ProvidedReferenceInfo? = null

        if (dotExpression != null) { // perhaps without this cond
            val resolvedDotCall = referenceResolver.tryResolveQualifier(targetElement)
            //println("$resolvedDotCall, name:  ${resolvedDotCall?.text}, fileName: ${resolvedDotCall?.containingFile?.text}")
            resolvedDotCall ?: return null
            referenceInfo = ProvidedReferenceInfo(resolvedDotCall)
        }

        when (searchStrategy) {
            SearchStrategy.DECLARATION ->
                getProperDeclarationsForScriptOrClass(element).firstOrNull {
                    it.name == targetElement.text && it.isPublic
                            && it.containingKtFile.getUserData(CELL_WAS_COMPILED) != null
                            && tryMatchWithDeclaration(targetElement, it, referenceInfo)
                }?.let { mutableListOf(it) }
            else -> getProperUsagesForTargetElement(element, targetElement)
        }?.also {
            return it
        }


        if (declaredPublicClasses.isEmpty()) return null

        for (declaredPublicClass in declaredPublicClasses) {
            if (ans != null) continue
            traverseChildrenAndSearch(declaredPublicClass, targetElement, searchStrategy)?.let { recResult ->
                ans = recResult
            }
        }

        return ans
    }

    private fun getProperUsagesForTargetElement(element: PsiElement, targetElement: PsiElement): MutableList<NavigatablePsiElement> {
        val ans = mutableListOf<NavigatablePsiElement>()
        val targetName = targetElement.text
        val targetDeclaration = targetElement.parentOfType<KtDeclaration>(true)!!
        element.containingFile.acceptChildren(object : PsiRecursiveElementVisitor(){
            override fun visitElement(element: PsiElement) {
                if ((element.elementType is KtNameReferenceExpressionElementType || element is KtCallExpression)
                    && element.textMatches(targetName)) {
                    val resolvedRefInfo = referenceResolver.tryResolveQualifier(element)
                    if (targetDeclaration.containingFile == resolvedRefInfo?.containingFile) {
                        ans.add(element as KtElement)
                    } else if (resolvedRefInfo != null && tryMatchWithDeclaration(targetElement, targetDeclaration, ProvidedReferenceInfo(resolvedRefInfo))) {
                        ans.add(element as KtElement)
                    }
                }
                super.visitElement(element)
            }
        })

        return ans
    }

    private fun tryMatchWithDeclaration(targetElement: PsiElement, candidateDeclaration: KtDeclaration, referenceInfo: ProvidedReferenceInfo?): Boolean {
        referenceInfo ?: return true
        val candidateEnclosingClassOrThis = candidateDeclaration.parentOfType<KtClass>(withSelf = true)
        if (candidateEnclosingClassOrThis == null) {
            val compiledClassName = candidateDeclaration.containingKtFile.getUserData(CELL_WAS_COMPILED)!!
            return compiledClassName == referenceInfo.enclosingClass?.name
        }

        return candidateEnclosingClassOrThis.name == referenceInfo.enclosingClass?.name
    }

    private fun getProperDeclarationsForScriptOrClass(element: PsiElement): Array<KtDeclaration> {
        return when (element) {
            is KtScript -> element.blockExpression.getChildrenOfType<KtDeclaration>()
            is KtBlockExpression -> element.getChildrenOfType<KtDeclaration>()
            is KtClass -> if (element.body != null) getProperDeclarationsForScriptOrClass(element.body!!) else emptyArray()
            is KtClassBody -> element.getChildrenOfType<KtDeclaration>()
            else -> element.getChildrenOfType<KtDeclaration>()
        }
    }


}