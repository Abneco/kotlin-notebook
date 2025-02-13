// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKotlinNotebookDelegatingHighlightingVisitorAdapter<T: AbstractHighlightingVisitor> : AbstractKotlinNotebookHighlightingVisitorAdapter() {
    private var visitor: T? = null

    protected abstract fun createVisitor(holder: HighlightInfoHolder): T
    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile
                && InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)
                && InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file).virtualFile.isKotlinNotebook
    }

    override fun visit(element: PsiElement) {
        visitor?.let { element.accept(it) }
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        try {
            visitor = createVisitor(holder)
            action.run()

            return true
        } finally {
            try {
                analysisFinished(file)
            } finally {
                visitor = null
            }
        }
    }
}
