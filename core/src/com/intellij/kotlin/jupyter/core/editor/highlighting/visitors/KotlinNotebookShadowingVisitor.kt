// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiFile

/**
 * `KotlinNotebookShadowingVisitor` is responsible for managing and performing
 *  special highlighting style for errors outside the focus cell.
 *
 *  This special style is referred to as "Shadowing".
 *
 *  Since inside main logic front-end api is used, there is no need to specifically traverse the file;
 *  hence, KotlinNotebookDummyVisitor is used as a visitor.
 */
internal class KotlinNotebookShadowingVisitor : AbstractKotlinNotebookHighlightingVisitorAdapter() {
    override fun clone(): HighlightVisitor {
        return KotlinNotebookShadowingVisitor()
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val result = KotlinPluginModeShadowingAnalyzerHandler
            .create()
            .performShadowing(file, updateWholeFile, holder) { analysisFinished(file) }

        action.run()

        return result
    }
}
