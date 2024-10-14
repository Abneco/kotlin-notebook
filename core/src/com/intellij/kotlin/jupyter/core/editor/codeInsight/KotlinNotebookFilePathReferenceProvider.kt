// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebookFile
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FilePathReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getContentRange
import org.jetbrains.kotlin.psi.psiUtil.isPlain
import org.jetbrains.kotlin.psi.psiUtil.plainContent

class KotlinNotebookFilePathReferenceProvider : PsiReferenceContributor() {
    object KotlinFilePathReferenceProvider : FilePathReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<out PsiReference> {
            if (element !is KtStringTemplateExpression) return PsiReference.EMPTY_ARRAY
            if (!element.isPlain()) return PsiReference.EMPTY_ARRAY
            if (!element.isInsideKotlinNotebookFile()) return PsiReference.EMPTY_ARRAY

            return object : FileReferenceSet(
                element.plainContent, element, element.getContentRange().startOffset,
                this, false, false
            ) {
                override fun computeDefaultContexts(): MutableCollection<PsiFileSystemItem> {
                    return ArrayList<PsiFileSystemItem>().apply {
                        val file = containingFile?.virtualFile
                        if (file != null) {
                            addAll(toFileSystemItems(file.parent))
                        }
                    }
                }

                override fun isSoft(): Boolean {
                    return true
                }

                override fun isAbsolutePathReference(): Boolean {
                    return true
                }

                override fun couldBeConvertedTo(relative: Boolean): Boolean {
                    return !relative
                }

                override fun absoluteUrlNeedsStartSlash(): Boolean {
                    val s = pathString
                    return s != null && !s.isEmpty() && s[0] == '/'
                }

                override fun createFileReference(range: TextRange?, index: Int, text: String?): FileReference? {
                    return createFileReference(this, range, index, text)
                }

                override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem?> {
                    return Condition { element1: PsiFileSystemItem? -> isPsiElementAccepted(element1) }
                }
            }.allReferences
        }
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(KtStringTemplateExpression::class.java),
            KotlinFilePathReferenceProvider,
            PsiReferenceRegistrar.LOWER_PRIORITY
        )
    }
}
