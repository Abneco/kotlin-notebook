// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractAnnotationHolderHighlightingVisitor
import org.jetbrains.kotlinx.jupyter.plugin.editor.AbstractKotlinHighlightingVisitorAdapter


internal class KotlinNotebookBeforeHighlightingVisitor: AbstractKotlinHighlightingVisitorAdapter<KotlinNotebookDummyVisitor>(
    { annotationHolder -> KotlinNotebookDummyVisitor(annotationHolder) }
) {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookBeforeHighlightingVisitor()
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        prepareForFileAndAdjust(file, holder, stage = PassStage.MarkTargetHostBeforeHighlighting)
        return true
    }
}

internal class KotlinNotebookDummyVisitor(holder: AnnotationHolder) : AbstractAnnotationHolderHighlightingVisitor(holder) {
    override fun visitFile(file: PsiFile) {
        return
    }
}
