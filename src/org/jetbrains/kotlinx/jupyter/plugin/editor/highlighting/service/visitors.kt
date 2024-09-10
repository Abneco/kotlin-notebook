// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.visitors.AbstractKotlinHighlightingVisitorAdapter
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.visitors.KtFilePluginModeShadowingAnalyzerHandler


internal class KotlinNotebookBeforeHighlightingVisitor: AbstractKotlinHighlightingVisitorAdapter<KotlinNotebookDummyVisitor>() {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookBeforeHighlightingVisitor()
    }

    override fun createVisitor(holder: HighlightInfoHolder): KotlinNotebookDummyVisitor {
        return KotlinNotebookDummyVisitor(holder)
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        return KtFilePluginModeShadowingAnalyzerHandler
            .create()
            .performShadowing(file, updateWholeFile, holder, { analysisFinished(file, holder) })
    }
}

internal class KotlinNotebookDummyVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder) {
    override fun visitFile(file: PsiFile) {
    }
}
