// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.openapi.util.Key
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType

enum class ReferenceSearchStrategy {
    DECLARATION,
    REFERENCES
}

object NotebookReferenceFinder {
    // Holds compiled class name
    val CELL_CLASS_NAME: Key<String> = Key.create("COMPILED_CELL_SCRIPT_CLASS_NAME")

    private val referenceResolver = NotebookReferenceExpressionResolver

    private val declarationsCollectingVisitor = ScriptDeclarationsCollectingVisitor()

    fun traverseChildrenAndSearch(injectionHost: PsiLanguageInjectionHost, element: PsiElement, targetElement: PsiElement,
                                  searchStrategy: ReferenceSearchStrategy = ReferenceSearchStrategy.DECLARATION,
                                  foundData: MutableList<NavigatablePsiElement>?): Unit {
        val resolvedNullableRef = targetElement.reference?.resolve()
        if (resolvedNullableRef?.containingFile?.fileType?.defaultExtension == "kt") return

        val dotExpression = targetElement.getParentOfType<KtDotQualifiedExpression>(false)

        val referenceInfo: ProvidedReferenceInfo? = if (dotExpression != null) { // perhaps without this cond
            val asArgument = targetElement.getParentOfType<KtValueArgumentList>(false)
            val resolvedDotCall = tryResolveQualifierInDotExpression(dotExpression, targetElement)
            //println("$resolvedDotCall, name:  ${resolvedDotCall?.text}, fileName: ${resolvedDotCall?.containingFile?.text}")
            if (resolvedDotCall == null && asArgument == null) return
            targetElement.tryResolveQualifierToReferenceInfo()
        } else {
            targetElement.tryResolveQualifierToReferenceInfo()
        }
        val isSameFile = element.containingFile == targetElement.containingFile

        when (searchStrategy) {
            ReferenceSearchStrategy.DECLARATION ->
                getProperDeclarationsForScriptOrClass(element, dotExpression != null).firstOrNull {
                    val declarationMatchResult = if (referenceInfo != null)
                                                    tryMatchWithDeclaration(injectionHost, targetElement, it, referenceInfo)
                                                 else isSameFile
                    it.name == targetElement.text && it.isPublic
                            //&& it.containingKtFile.getUserData(CELL_CLASS_NAME) != null
                            && declarationMatchResult
                }?.let { listOf(it) }
            ReferenceSearchStrategy.REFERENCES -> getProperUsagesForTargetElement(injectionHost, element, targetElement)
        }?.let {
            foundData?.addAll(it)
            return
        }


        val declaredPublicClasses = element.children.filter {
            it is KtClass && it.isPublic
        }
        if (declaredPublicClasses.isEmpty()) return

        for (declaredPublicClass in declaredPublicClasses) {
            //if (foundData != null) continue
            traverseChildrenAndSearch(injectionHost, declaredPublicClass, targetElement, searchStrategy, foundData)
        }
    }

    private fun getProperUsagesForTargetElement(injectionHost: PsiLanguageInjectionHost, element: PsiElement, targetElement: PsiElement): List<NavigatablePsiElement> {
        val ans = mutableListOf<NavigatablePsiElement>()
        val targetName = targetElement.text
        val targetDeclaration = targetElement.parentOfType<KtDeclaration>(true)!!
        element.containingFile.acceptChildren(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if ((element.elementType is KtNameReferenceExpressionElementType || element is KtCallExpression)
                    // collect all similar expressions and then decide do they correspond to a one KtFile
                    && element.textMatches(targetName)) {
                    //val properNameElement = if (element is KtCallExpression) element.calleeExpression else element
                    val resolvedRefInfo = referenceResolver.tryResolveQualifier(element)
                    if (targetDeclaration.containingFile == resolvedRefInfo?.containingFile) {
                        ans.add(element as KtElement)
                        // if not then tryMatch class with class present in compiled sources
                    } else if (resolvedRefInfo != null && tryMatchWithDeclaration(injectionHost, targetElement, targetDeclaration, ProvidedReferenceInfo(resolvedRefInfo))) {
                        ans.add(element as KtElement)
                    }
                }
                super.visitElement(element)
            }
        })

        return ans
    }

    private fun tryMatchWithDeclaration(host: PsiLanguageInjectionHost, targetElement: PsiElement, candidateDeclaration: KtDeclaration, referenceInfo: ProvidedReferenceInfo): Boolean {
        candidateDeclaration.parentOfType<KtClass>(withSelf = true)?.let {
            return it.name == referenceInfo.enclosingClass?.name
        }

        val compiledClassName = candidateDeclaration.containingKtFile.getUserData(CELL_CLASS_NAME)
                                ?: host.getUserData(CELL_CLASS_NAME) ?: ""
        return compiledClassName == referenceInfo.enclosingClass?.name
    }

    private fun getProperDeclarationsForScriptOrClass(element: PsiElement, isPartOfDotCall: Boolean = false): Array<KtDeclaration> {
        return when (element) {
            is KtScript -> element.blockExpression.getChildrenOfType<KtDeclaration>()
            is KtBlockExpression -> {
                val children = element.getChildrenOfType<KtDeclaration>()
                if (isPartOfDotCall) {
                   return declarationsCollectingVisitor.collectAllNestedDeclarationsPresent(children).toTypedArray()
                }
                children
            }
            is KtClass -> element.body?.let { getProperDeclarationsForScriptOrClass(it) } ?: emptyArray()
            is KtObjectDeclaration -> element.declarations.let {
                if (isPartOfDotCall)
                    declarationsCollectingVisitor.collectAllNestedDeclarationsPresent(it).toTypedArray()
                else (it + element).toTypedArray()
            }
            is KtClassBody -> element.getChildrenOfType<KtDeclaration>()
            is KtDeclaration -> arrayOf(element)
            else -> element.getChildrenOfType<KtDeclaration>()
        }
    }

    private fun tryResolveQualifierInDotExpression(element: KtDotQualifiedExpression, targetElement: PsiElement): PsiElement? {
        var currentReceiver = element.receiverExpression
        while (currentReceiver is KtDotQualifiedExpression) {
            currentReceiver = currentReceiver.receiverExpression
        }
        return referenceResolver.tryResolveQualifier(currentReceiver.navigationElement) 
            ?: (targetElement.parent as? KtReferenceExpression)?.let { ref ->
                referenceResolver.tryResolveQualifier(ref)
            }
    }

    private fun PsiElement.tryResolveQualifierToReferenceInfo(): ProvidedReferenceInfo? {
        return referenceResolver.tryResolveQualifier(this)?.let {
            ProvidedReferenceInfo(it)
        }
    }

}