// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.restarter

import com.intellij.psi.PsiFile

/**
 * Marker interface to specify a class that could handle highlighting restart requests
 * with respect to the Kotlin Notebook logic.
 * @see NotebookHighlightingRestarter
 */
fun interface HighlightingRestarter {
    fun restartHighlighting(file: PsiFile)
}
