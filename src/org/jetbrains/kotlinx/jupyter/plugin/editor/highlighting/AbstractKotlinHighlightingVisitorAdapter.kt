// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.InjectedFileHighlightingHelper
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.highlightingManagerFor
import org.jetbrains.kotlinx.jupyter.plugin.util.getTopLevelFile

abstract class AbstractKotlinHighlightingVisitorAdapter<T: AbstractHighlightingVisitor>(
    protected val visitorFactory: (HighlightInfoHolder) -> T,
    private val isShouldUseNewHighlighting: Boolean = true // 0 if default
) : HighlightVisitor {
    private var visitor: T? = null
    protected var highlightingHelper: InjectedFileHighlightingHelper? = null

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile && InjectedLanguageUtilBase.getHighlightTokens(file) != null
    }

    override fun visit(element: PsiElement) {
        visitor?.let { element.accept(it) }
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        try {
            visitor = visitorFactory(holder)
            action.run()

            return true
        } finally {
            highlightingManagerFor(file.project, file.virtualFile.getTopLevelFile())?.finishedAnalysisForFile(file, holder)
            visitor = null
        }
    }

    protected fun prepareForFile(injectedFile: PsiFile) {
        highlightingHelper = InjectedFileHighlightingHelper(injectedFile)

        highlightingHelper?.markTargetHost()
    }
}
