// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.lang.injection.InjectedLanguageManager
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
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
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

    fun tryResolveCompiledDeclaration(psiElement: PsiElement, searchTargets: Collection<PsiLanguageInjectionHost>): PsiElement? {
        val project = psiElement.project
        val injectionManager = InjectedLanguageManager.getInstance(project)
        val targetName = psiElement.containingFile.name.removeSuffix(".class")

        searchTargets.firstOrNull { host ->
            val compiledName = host.getUserData(CELL_CLASS_NAME) ?: return@firstOrNull false
            compiledName == targetName
        }?.let {
            val asPsiFile = injectionManager.getInjectedPsiFiles(it)?.firstOrNull()?.first as? KtFile ?: return null
            val ans = mutableListOf<NavigatablePsiElement>()
            traverseChildrenAndSearch(injectionManager, it, targetName, asPsiFile, psiElement, foundData = ans)
            return ans.firstOrNull()
        }

        return null
    }

    fun traverseChildrenAndSearch(injectionManager: InjectedLanguageManager, injectionHost: PsiLanguageInjectionHost,
                                  possibleClassName: String?, element: PsiElement, targetElement: PsiElement,
                                  searchStrategy: ReferenceSearchStrategy = ReferenceSearchStrategy.DECLARATION,
                                  foundData: MutableList<NavigatablePsiElement>?): Unit {
        val resolvedNullableRef = targetElement.reference?.resolve()
        //    ?: (targetElement.parent as? KtReferenceExpression)?.mainReference?.resolve()
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
        val declarations = if (searchStrategy == ReferenceSearchStrategy.DECLARATION)
                                getProperDeclarationsForScriptOrClass(element, dotExpression != null)
                            else element.children

        when (searchStrategy) {
            ReferenceSearchStrategy.DECLARATION -> {
                if (targetElement is KtPrimaryConstructor && referenceInfo?.enclosingClass != null && (element as? KtClass)?.name == referenceInfo.enclosingClass?.name) {
                    foundData?.add(element as NavigatablePsiElement)
                    return
                }
                if (referenceInfo?.enclosingClass != null && declarations.any { referenceInfo.enclosingClass?.name == (it as? KtClass)?.name }) {
                    null
                } else declarations.firstOrNull {
                    it as KtDeclaration
                    it ?: return@firstOrNull false
                    val declarationMatchResult = if (referenceInfo != null)
                            tryMatchWithDeclaration(injectionHost, possibleClassName, targetElement, it, referenceInfo)
                        else true
                    val nameToCompare = if (targetElement is KtDeclaration) targetElement.name else targetElement.text
                    it.name == nameToCompare && it.isPublic
                            //&& it.containingKtFile.getUserData(CELL_CLASS_NAME) != null
                            && declarationMatchResult
                }?.let { listOf(it as NavigatablePsiElement) }
            }
            ReferenceSearchStrategy.REFERENCES -> getProperUsagesForTargetElement(injectionHost, possibleClassName, element, targetElement)
        }?.let {
            foundData?.addAll(it)
            return
        }


        val declaredPublicClasses = declarations.filter {
            it is KtClass && it.isPublic
        }
        if (declaredPublicClasses.isEmpty()) return

        for (declaredPublicClass in declaredPublicClasses) {
            //if (foundData != null) continue
            traverseChildrenAndSearch(injectionManager, injectionHost, possibleClassName, declaredPublicClass, targetElement, searchStrategy, foundData)
        }
    }

    private fun getProperUsagesForTargetElement(injectionHost: PsiLanguageInjectionHost, possibleClassName: String?, element: PsiElement, targetElement: PsiElement): List<NavigatablePsiElement> {
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
                    if (targetDeclaration.containingFile == resolvedRefInfo?.containingFile && element.reference?.isReferenceTo(targetDeclaration) == true) {
                        ans.add(element as KtElement)
                        // if not then tryMatch class with class present in compiled sources
                    } else if (resolvedRefInfo != null && tryMatchWithDeclaration(injectionHost, possibleClassName, targetElement, targetDeclaration, ProvidedReferenceInfo(resolvedRefInfo))) {
                        ans.add(element as KtElement)
                    }
                }
                super.visitElement(element)
            }
        })

        return ans
    }

    private fun tryMatchWithDeclaration(host: PsiLanguageInjectionHost, possibleClassName: String?, targetElement: PsiElement, candidateDeclaration: KtDeclaration, referenceInfo: ProvidedReferenceInfo): Boolean {
        candidateDeclaration.parentOfType<KtClass>(withSelf = true)?.let {
            return it.name == referenceInfo.enclosingClass?.name
        }

        val compiledClassName = possibleClassName
                                ?: host.getUserData(CELL_CLASS_NAME)
                                ?: candidateDeclaration.containingKtFile.getUserData(CELL_CLASS_NAME)
                                ?: ""
        return compiledClassName == referenceInfo.enclosingClass?.name
    }

    private fun getProperDeclarationsForScriptOrClass(element: PsiElement, isPartOfDotCall: Boolean = false): Array<KtDeclaration> {
        //val enclosingClass = targetInfo?.enclosingClass
        return when (element) {
            is KtFile -> element.getChildrenOfType<KtScript>().firstOrNull()?.let { getProperDeclarationsForScriptOrClass(it) } ?: emptyArray()
            is KtScript -> element.blockExpression.getChildrenOfType<KtDeclaration>()
            is KtBlockExpression -> {
                val children = element.getChildrenOfType<KtDeclaration>()
                if (isPartOfDotCall) {
                   return declarationsCollectingVisitor.collectAllNestedDeclarationsPresent(children).toTypedArray()
                }
                children
            }
            is KtClass -> element.body?.let {
                val inConstructorElems = element.primaryConstructor?.valueParameters?.filter { p -> p.isPropertyParameter() }?.toTypedArray()
                val classElems = getProperDeclarationsForScriptOrClass(it)
                if (inConstructorElems.isNullOrEmpty()) classElems else classElems + inConstructorElems
            } ?: emptyArray()
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
        val compiledClassCase = containingFile.name.endsWith(".class") && parentOfType<KtClass>() != null

        return referenceResolver.tryResolveQualifier(this)?.let {
            ProvidedReferenceInfo(it)
        } ?: if (compiledClassCase) ProvidedReferenceInfo(this) else null
    }

}