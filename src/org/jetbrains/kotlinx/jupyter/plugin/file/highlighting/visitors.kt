// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractAnnotationHolderHighlightingVisitor
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.suppressHighlight
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.unsuppressHighlight
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.AbstractKotlinHighlightingVisitorAdapter
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.HighlightInfoManipulator.convertToShadowedDeclaration


internal class KotlinNotebookBeforeHighlightingVisitor: AbstractKotlinHighlightingVisitorAdapter<KotlinNotebookDummyVisitor>(
    { annotationHolder -> KotlinNotebookDummyVisitor(annotationHolder) }
) {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookBeforeHighlightingVisitor()
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        if (file !is KtFile) return true
        prepareForFile(file)
        val helper = highlightingHelper!!

        val isTargetHost = helper.isCurrentFileTarget

        if (isTargetHost) {
            file.unsuppressHighlight()
            return true
        }

        try {
            val seenInfos = mutableSetOf<HighlightInfo>()
            file.analyzeWithAllCompilerChecks(
                {
                    if (it.severity == Severity.ERROR) {
                        val element = it.psiElement as? KtElement
                        element?.suppressHighlight()
                        if (!helper.isShouldAcceptDiagnostic(it)) return@analyzeWithAllCompilerChecks

                        val info = convertToShadowedDeclaration(it)

                        if (info != null) {
                            seenInfos.add(info)
                        } else thisLogger().warn("Cannot convert diagnostic to shadowed: $it")
                    }
                }
            )

            helper.applyReceivedHighlightInfos(seenInfos, holder)
        } catch (t: Throwable) {
            thisLogger().warn("Exception during analyze: $t")
            return false
        }

        return true
    }
}

internal class KotlinNotebookDummyVisitor(holder: AnnotationHolder) : AbstractAnnotationHolderHighlightingVisitor(holder) {
    override fun visitFile(file: PsiFile) {
        return
    }
}
