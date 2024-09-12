// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor

internal class KotlinNotebookShadowingVisitor : AbstractKotlinHighlightingVisitorAdapter<KotlinNotebookDummyVisitor>() {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookShadowingVisitor()
    }

    override fun createVisitor(holder: HighlightInfoHolder): KotlinNotebookDummyVisitor {
        return KotlinNotebookDummyVisitor(holder)
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        return KotlinPluginModeShadowingAnalyzerHandler
            .create()
            .performShadowing(file, updateWholeFile, holder, { analysisFinished(file, holder) })
    }
}


internal class KotlinNotebookDummyVisitor(holder: HighlightInfoHolder) : AbstractHighlightingVisitor(holder) {
    override fun visitElement(element: PsiElement) = Unit

    override fun visitFile(file: PsiFile) = Unit
}