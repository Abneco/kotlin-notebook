// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

import com.intellij.execution.filters.Filter
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebookBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware

/**
 * Class responsible for adding a link from a kernel stack trace to the relevant line in the
 * notebook editor, rather than linking to the compiled class, which is an implementation detail.
 *
 * The compiled class will still show up in the stack trace, but it should be very easy to jump
 * to the offending code inside the Notebook.
 */
internal class KotlinNotebookLineLinkFilter(val editor: EditorImpl) : Filter, DumbAware, Disposable {
    private val backedNotebookVirtualFile = editor.notebookFile
    val notebook = backedNotebookVirtualFile.notebook as JupyterNotebookBase

    override fun dispose() {}

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val stackLineInfo = linkifyStackLine(line, entireLength) ?: return null
        val hyperlinkInfo = KotlinCellHyperlinkInfo(stackLineInfo.executionCount, stackLineInfo.cellLine, this)
        return Filter.Result(stackLineInfo.highlightRange.first, stackLineInfo.highlightRange.last, hyperlinkInfo)
    }
}
