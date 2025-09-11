// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.InjectedFileData
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

/**
 * Data class used to represent pass setup upon initialization.
 * Info is constructed using [com.intellij.kotlin.jupyter.core.editor.highlighting.components.queue.HighlightingEvent].
 */
internal data class NotebookPassConfiguration(
  val focusCell: Int,
  val filesToHL: Map<KtFile, InjectedFileData>,
  val editorCells: List<PsiLanguageInjectionHost>
) {
    val cellIndexesToHighlight: Set<Int>
        get() = filesToHL.mapTo(mutableSetOf()) { it.value.notebookCellIndex }

    companion object {
        val EMPTY = NotebookPassConfiguration(-1, emptyMap(), emptyList())
    }
}