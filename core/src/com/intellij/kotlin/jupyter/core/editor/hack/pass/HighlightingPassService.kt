// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass

import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.DaemonState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

/**
 * Contract of a file-level API any Notebook Highlighting service should follow.
 *
 * Note this is a per-file service.
 * @see [com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService]
 */
internal interface HighlightingPassService {
    val passState: DaemonState

    fun getRangesToHighlight(file: PsiFile, editor: Editor): Collection<TextRange>

    fun passFinished(editor: Editor, file: PsiFile)

    fun shouldHighlightErrorsInFile(ktFile: KtFile): Boolean
}