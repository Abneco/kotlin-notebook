// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithAllCompilerChecks
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.suppressHighlight
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinHighlightVisitor.Companion.unsuppressHighlight
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.AbstractKotlinHighlightingVisitorAdapter
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.HighlightInfoManipulator.convertToShadowedDeclaration
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile


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
        if (helper.topLevelFile !is JupyterFile) return true

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
        } catch (e: Throwable) {
            if (e is ProcessCanceledException) {
                throw e
            }
            thisLogger().warn("Exception during analyze", e)
            return false
        }

        return true
    }
}

internal class KotlinNotebookDummyVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder) {
    override fun visitFile(file: PsiFile) {
    }
}
