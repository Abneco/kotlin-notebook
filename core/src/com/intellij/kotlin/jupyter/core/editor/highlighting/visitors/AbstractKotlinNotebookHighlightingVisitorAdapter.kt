// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.highlightingManagerFor
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrSelf
import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebookFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class AbstractKotlinNotebookHighlightingVisitorAdapter : HighlightVisitor {
    override fun suitableForFile(file: PsiFile): Boolean {
        return file.isInsideKotlinNotebookFile()
    }

    protected fun analysisFinished(file: PsiFile) {
        highlightingManagerFor(file.project, file.virtualFile.getTopLevelFileOrSelf())?.finishedAnalysisForFile(file)
    }

    override fun visit(element: PsiElement) {
    }
}