// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.error

import com.intellij.execution.filters.Filter
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.outputs.error.JupyterErrorOutputFiltersProvider
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile

/**
 * This class adds Kotlin Notebook-specific [Filter]s for creating custom links in the exception
 * text sent from the kernel that is displayed in
 * [org.jetbrains.plugins.notebooks.jupyter.editor.outputs.error.JupyterErrorOutputConsoleView]
 */
class KotlinJupyterErrorOutputFiltersProvider: JupyterErrorOutputFiltersProvider {
  override fun getFilters(editor: EditorImpl, exceptionType: String, exceptionValue: String): List<Filter> {
      val notebook: BackedNotebookVirtualFile? = editor.virtualFile.toBackedNotebookFile()
      return when(notebook?.notebook?.language?.id) {
          "kotlin" -> listOf(KotlinNotebookLineLinkFilter(editor))
          else -> emptyList()
      }
  }
}
