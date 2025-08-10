// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass.state

import com.intellij.kotlin.jupyter.core.editor.hack.NotebookPassConfiguration
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile

/**
 * Instances of this interface should be responsible for tracking and providing information
 * about highlighting pass progress, e.g., how many files are highlighted, etc.
 *
 * This information is stored inside [NotebookPassConfiguration] and to be rebuilt on every pass.
 */
internal interface PassProgressTracker {
    val passConfiguration: NotebookPassConfiguration?

    fun passStarting(file: PsiFile, focusCellIndex: Int, indexesToHighlight: Collection<Int>, cellsToHighlight: List<PsiLanguageInjectionHost>)

    fun getRemainingProgressAfterPassFinished(editor: EditorEx): NotebookPassProgressRemains

    val leftToHighlight: Collection<KtFile>
}