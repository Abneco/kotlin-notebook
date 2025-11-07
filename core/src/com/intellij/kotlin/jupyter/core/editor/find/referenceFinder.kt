// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.find

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructurePerFileTracker.Companion.CELL_CLASS_NAME
import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookStructureTrackerService
import com.intellij.kotlin.jupyter.core.util.NOTEBOOK_COMPILED_CLASS_NAME_PREFIX
import com.intellij.kotlin.jupyter.core.util.NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.isPublic

enum class ReferenceSearchStrategy {
    DECLARATION,
    REFERENCES
}

object NotebookReferenceFinder {
    private val classRegex = Regex("$NOTEBOOK_COMPILED_CLASS_NAME_PREFIX.+$NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX")

    private val declarationsCollectingVisitor = ScriptDeclarationsCollectingVisitor()

    private fun Collection<PsiLanguageInjectionHost>.findPsiCellRelatedToCompiledClass(project: Project, notebookFile: BackedNotebookVirtualFile, className: String): PsiLanguageInjectionHost? {
        val hostFromService = NotebookStructureTrackerService.getForFile(project, notebookFile).findPsiCellByClassName(className)
        if (hostFromService != null) return hostFromService
        // fallback: traverse by stored class names related to a PSI
        return firstOrNull { host ->
            host.getUserData(CELL_CLASS_NAME)?.contains(className) == true
        }
    }

    fun tryResolveCompiledDeclaration(notebookFile: BackedNotebookVirtualFile, psiElement: PsiElement, searchTargets: Collection<PsiLanguageInjectionHost>): PsiElement? {
        val project = psiElement.project
        val injectionManager = InjectedLanguageManager.getInstance(project)
        val targetName = psiElement.containingFile.name.removeSuffix(".class")
        val isNavigationTargetCellClassItself = (psiElement as? KtClass)?.name?.matches(classRegex) == true

        val containingCell = searchTargets.findPsiCellRelatedToCompiledClass(project, notebookFile, targetName) ?: return null
        val asPsiFile = injectionManager.getInjectedPsiFiles(containingCell)?.firstOrNull()?.first as? KtFile ?: return null
        if (isNavigationTargetCellClassItself) return asPsiFile

        val ans = mutableListOf<NavigatablePsiElement>()
        traverseChildrenAndSearch(injectionManager, containingCell, setOf(targetName), asPsiFile, psiElement, foundData = ans)
        return ans.firstOrNull()
    }

    fun traverseChildrenAndSearch(
        injectionManager: InjectedLanguageManager,
        injectionHost: PsiLanguageInjectionHost,
        possibleClassNames: Set<String>,
        element: PsiElement,
        targetElement: PsiElement,
        searchStrategy: ReferenceSearchStrategy = ReferenceSearchStrategy.DECLARATION,
        foundData: MutableList<NavigatablePsiElement>?
    ) {
        val resolvedNullableRef = targetElement.reference?.resolve()
        if (resolvedNullableRef?.containingFile?.fileType?.defaultExtension == "kt") return

        val dotExpression = targetElement.getParentOfType<KtDotQualifiedExpression>(false)

        val referenceInfo: ProvidedReferenceInfo? = if (dotExpression != null) { // perhaps without this cond
            val asArgument = targetElement.getParentOfType<KtValueArgumentList>(false)
            val resolvedDotCall = tryResolveQualifierInDotExpression(dotExpression, targetElement)
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
                if (targetElement is KtPrimaryConstructor && referenceInfo?.enclosingClass != null && (element as? KtClassOrObject)?.name == referenceInfo.enclosingClass?.name) {
                    foundData?.add(element as NavigatablePsiElement)
                    return
                }
                if (referenceInfo?.enclosingClass != null && declarations.any { referenceInfo.enclosingClass?.name == (it as? KtClassOrObject)?.name }) {
                    null
                } else declarations.firstOrNull {
                    if (it !is KtDeclaration) return@firstOrNull false
                    val declarationMatchResult = if (referenceInfo != null)
                            tryMatchWithDeclaration(injectionHost, possibleClassNames, it, referenceInfo)
                        else true
                    val nameToCompare = if (targetElement is KtDeclaration) targetElement.name else targetElement.text
                    it.name == nameToCompare && it.isPublic
                            && declarationMatchResult
                }?.let { listOf(it as NavigatablePsiElement) }
            }
            ReferenceSearchStrategy.REFERENCES -> getUsagesForTargetElement(injectionHost, possibleClassNames, element, targetElement)
        }?.let {
            foundData?.addAll(it)
            return
        }


        val declaredPublicClasses = declarations.filter {
            it is KtClass && it.isPublic
        }
        if (declaredPublicClasses.isEmpty()) return

        for (declaredPublicClass in declaredPublicClasses) {
            traverseChildrenAndSearch(injectionManager, injectionHost, possibleClassNames, declaredPublicClass, targetElement, searchStrategy, foundData)
        }
    }

    private fun getUsagesForTargetElement(injectionHost: PsiLanguageInjectionHost, possibleClassNames: Set<String>, element: PsiElement, targetElement: PsiElement): List<NavigatablePsiElement> {
        val result = mutableListOf<NavigatablePsiElement>()
        val targetName: String? = if (targetElement is KtObjectDeclaration) targetElement.nameAsSafeName.asString() else targetElement.text
        val targetDeclaration = targetElement.parentOfType<KtDeclaration>(true) ?: return result
        element.containingFile.acceptChildren(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if ((element is KtNameReferenceExpression || element is KtCallExpression)
                    // collect all similar expressions and then decide do they correspond to a one KtFile
                    && targetName != null && element.textMatches(targetName)) {
                    val resolvedRefInfo = NotebookReferenceExpressionResolver.tryResolveQualifier(element)
                    if (targetDeclaration.containingFile == resolvedRefInfo?.containingFile && element.reference?.isReferenceTo(targetDeclaration) == true) {
                        result.add(element as KtElement)
                        // if not then tryMatch class with class present in compiled sources
                    } else if (resolvedRefInfo != null && tryMatchWithDeclaration(
                            injectionHost,
                            possibleClassNames,
                            targetDeclaration,
                            ProvidedReferenceInfo(resolvedRefInfo)
                        )) {
                        result.add(element as KtElement)
                    }
                }
                super.visitElement(element)
            }
        })

        return result
    }

    private fun tryMatchWithDeclaration(
        host: PsiLanguageInjectionHost,
        possibleClassName: Set<String>,
        candidateDeclaration: KtDeclaration,
        referenceInfo: ProvidedReferenceInfo
    ): Boolean {
        if (referenceInfo.resolvedTo !is KtClass) {
            candidateDeclaration.parentOfType<KtClass>(withSelf = true)?.let {
                return it.name == referenceInfo.enclosingClass?.name
            }
        }

        val compiledClassName = possibleClassName.ifEmpty {
            host.getUserData(CELL_CLASS_NAME)
        }

        return compiledClassName?.contains(referenceInfo.enclosingClass?.name) == true
    }

    private fun getProperDeclarationsForScriptOrClass(element: PsiElement, isPartOfDotCall: Boolean = false): Array<KtDeclaration> {
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
        return NotebookReferenceExpressionResolver.tryResolveQualifier(currentReceiver.navigationElement)
               ?: (targetElement.parent as? KtReferenceExpression)?.let { ref ->
                   NotebookReferenceExpressionResolver.tryResolveQualifier(ref)
            }
    }

    private fun PsiElement.tryResolveQualifierToReferenceInfo(): ProvidedReferenceInfo? {
        val compiledClassCase = containingFile.name.endsWith(".class") && parentOfType<KtClass>() != null

        return NotebookReferenceExpressionResolver.tryResolveQualifier(this)?.let {
            ProvidedReferenceInfo(it)
        } ?: if (compiledClassCase) ProvidedReferenceInfo(this) else null
    }

}