// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.file.InjectedFileHighlightingHelper

abstract class AbstractKotlinHighlightingVisitorAdapter<T: AbstractHighlightingVisitor>(
    private val visitorFactory: (AnnotationHolder) -> T,
    private val isShouldUseNewHighlighting: Boolean = false // 0 if default
) : HighlightVisitor {
    private var visitor: T? = null
    private var highlightingHelper: InjectedFileHighlightingHelper? = null

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile && InjectedLanguageUtilBase.getHighlightTokens(file) != null
    }

    override fun visit(element: PsiElement) {
        visitor?.let { element.accept(it) }
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        try {
            val annotationHolder = object : AnnotationHolderImpl(AnnotationSession(file), false) {
                override fun add(element: Annotation?): Boolean {
                    if (element != null) holder.add(HighlightInfo.fromAnnotation(element))
                    return true
                }
            }

            annotationHolder.runAnnotatorWithContext(file) { element, annoHolder ->
                visitor = visitorFactory(annoHolder)
                action.run()
            }

            if (isShouldUseNewHighlighting) {
                prepareForFileAndAdjust(file, holder)
            }

            return true
        } finally {
            visitor = null
        }
    }

    private fun prepareForFileAndAdjust(injectedFile: PsiFile, holder: HighlightInfoHolder) {
        if (highlightingHelper == null || highlightingHelper?.injectedFile != injectedFile) {
            highlightingHelper = InjectedFileHighlightingHelper(injectedFile)
        }
        highlightingHelper?.updateHolderOrProvided(holder)
    }
}
