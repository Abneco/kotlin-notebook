// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerBase
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.codeinsight.utils.ExplicitReceiverInfo
import org.jetbrains.kotlin.idea.codeinsight.utils.getLabelToBeReferencedByThis
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor


@OptIn(KaContextParameterApi::class, KaExperimentalApi::class)
context(_: KaSession)
fun KtElement.getImplicitReceivers(): List<KaImplicitReceiver> {
    val scopeContext = containingKtFile.scopeContext(this)
    return scopeContext.implicitReceivers
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
fun KtElement.getLastCompiledImplicitReceiverInfo(): ExplicitReceiverInfo? {
    val receivers = this.getImplicitReceivers()
    val lastRecent = receivers.firstOrNull() ?: return null
    val symbolType = lastRecent.type.symbol ?: return null

    return getLabelToBeReferencedByThis(symbolType)
}

/**
 * Finds all the [KtNamedDeclaration]s in the given [org.jetbrains.kotlin.psi.KtScript]
 */
@RequiresReadLock
internal fun KtFile.findAllDeclarations(): Collection<KtNamedDeclaration> {
    if (!isScript()) return emptyList()

    val namedElements: MutableList<KtNamedDeclaration> = mutableListOf()
    val namedElementVisitor = object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is KtNamedDeclaration) {
                namedElements.add(element)
            }
            super.visitElement(element)
        }
    }
    script?.acceptChildren(namedElementVisitor)

    return namedElements
}

internal inline fun <reified T: KtDeclaration> KtFile.findAllDeclarationsOfType(): Collection<T> {
    val declarations = runReadAction {
        findAllDeclarations()
    }
    return declarations.filterIsInstance<T>()
}


/**
 * Invokes search of all the references for given [KtNamedDeclaration]
 */
internal fun KtNamedDeclaration.getReferencesFromProvider(): Collection<PsiElement> {
    val references = mutableListOf<PsiElement>()
    val handler = (FindManager.getInstance(project) as FindManagerBase).findUsagesManager.getFindUsagesHandler(this, true)
    if (handler == null) return references

    val options = handler.findUsagesOptions.clone()
    options.isSearchForTextOccurrences = false
    handler.processElementUsages(this, Processor { usage ->
        val refElement = usage.element
        if (refElement == null) {
            return@Processor true
        }
        if (checkReference(refElement, this)) {
            references.add(refElement)
        }
        true
    }, options)

    return references
}

private fun checkReference(refElement: PsiElement, declaration: KtNamedDeclaration): Boolean {
    if (declaration.isAncestor(refElement)) return true // usages inside element's declaration are not counted
    val reference = refElement.reference?.resolve()
    if (reference == null) {
        return false
    }

    // let's not count usages from imports
    return refElement.getParentOfType<KtImportDirective>(false) == null
}