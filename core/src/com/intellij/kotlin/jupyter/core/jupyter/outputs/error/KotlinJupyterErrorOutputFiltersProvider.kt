// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

import com.intellij.execution.filters.Filter
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.outputs.error.JupyterErrorOutputFiltersProvider
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.openapi.editor.impl.EditorImpl

/**
 * This class adds Kotlin Notebook-specific [Filter]s for creating custom links in the exception
 * text sent from the kernel that is displayed in
 * [com.intellij.jupyter.core.jupyter.editor.outputs.error.JupyterErrorOutputConsoleView]
 */
class KotlinJupyterErrorOutputFiltersProvider : JupyterErrorOutputFiltersProvider {
    override suspend fun getFilters(editor: EditorImpl, exceptionType: String, exceptionValue: String): List<Filter> {
        val notebookFile: BackedNotebookVirtualFile? = editor.virtualFile?.toBackedNotebookFile()
        return if (notebookFile?.isKotlinNotebook == true) {
            listOf(KotlinNotebookLineLinkFilter(editor))
        } else {
            emptyList()
        }
    }
}
