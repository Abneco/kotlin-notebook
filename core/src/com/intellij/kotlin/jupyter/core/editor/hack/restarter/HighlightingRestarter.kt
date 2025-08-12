// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.restarter

import com.intellij.kotlin.jupyter.core.editor.hack.restarter.NotebookAnalysisRestarter.scheduleRegularUpdate
import com.intellij.kotlin.jupyter.core.editor.highlighting.HighlightingComponent
import com.intellij.psi.PsiFile

interface HighlightingRestarter {
    fun restartHighlighting(file: PsiFile)
}

internal object KotlinNotebookHighlightingRestarter : HighlightingRestarter, HighlightingComponent() {
    override fun restartHighlighting(file: PsiFile) {
        scheduleRegularUpdate(file)
    }
}