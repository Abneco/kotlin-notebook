// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType


internal class NotebookReferenceWrapper(
    private val resolvedTo: PsiElement,
    element: PsiElement,
    private val range: TextRange,
    private val soft: Boolean
): PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement? {
        return resolvedTo
    }

    override fun getValue(): String {
        return element.text
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        return range
    }

    override fun getRangeInElement(): TextRange {
        return range
    }

    override fun isSoft(): Boolean = soft
}

internal object NotebookReferenceExpressionResolver {
    fun tryResolveQualifier(element: PsiElement): PsiElement? {
        val referenceExpression = element.getParentOfType<KtNameReferenceExpression>(false) ?: return null
        val adjusted = retrieveNameReference(referenceExpression)
        referenceExpression.references.firstOrNull { it.resolve() != null }?.let {
            return it.resolve()
        }
        adjusted?.references?.firstOrNull {
            it.resolve() != null
        }?.let { return it.resolve() }

        return null
    }

    private fun retrieveNameReference(element: PsiElement): PsiElement? {
        var foundElement: PsiElement? = null
        element.acceptChildren(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is KtSimpleNameExpression) {
                    foundElement = element
                    return
                }
                super.visitElement(element)
            }
        })
        return foundElement
    }

}

internal data class ProvidedReferenceInfo(val resolvedTo: PsiElement) {
    val enclosingClass: KtClass? by lazy {
        resolvedTo.parentOfType()
    }

    val type = resolvedTo.elementType
}