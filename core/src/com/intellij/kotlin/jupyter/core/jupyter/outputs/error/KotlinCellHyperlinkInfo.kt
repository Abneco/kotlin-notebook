// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.jupyter.core.editor.JupyterExecutionHistoryProvider
import com.intellij.jupyter.core.fus.JupyterFeaturesCollector
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager

/**
 * Class wrapping the intent of navigating from a Kotlin Notebook stacktrace
 * shown in [com.intellij.jupyter.core.jupyter.editor.outputs.error.JupyterErrorOutputConsoleView]
 * to the line in the code cell that triggered the exception.
 */
internal class KotlinCellHyperlinkInfo(
  private val executionCount: Int,
  private val cellLine: Int,
  filter: KotlinNotebookLineLinkFilter,
) : HyperlinkInfo {
  private val editor: EditorImpl = filter.editor
  private val notebook = filter.notebook
  private val cellLines = NotebookCellLines.get(editor)

  override fun navigate(project: Project) {
    val index = getCurrentSessionIndex() ?: suggestCellIndex() ?: return
    val cellLines = cellLines.intervals[index]

    val line = cellLines.firstContentLine + cellLine - 1
    if (line > cellLines.lastContentLine) return
    val lineStartOffset = editor.document.getLineStartOffset(line)

    IdeFocusManager.getInstance(project).requestFocus(editor.component, true)
    editor.setMode(NotebookEditorMode.EDIT)
    editor.caretModel.moveToOffset(lineStartOffset)
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)

    JupyterFeaturesCollector.onLinkToCellInError()
  }
  /**
   * Look up the cell id using the current session history. If this lookup succeeds,
   * the returned cell index should be correct.
   */
  private fun getCurrentSessionIndex(): Int? {
      val executionHistoryManager = JupyterExecutionHistoryProvider.getOrInstall(editor)
      val cellId = executionHistoryManager.getIdForExecutionCount(executionCount) ?: return null
      val cell = notebook.computeCells().firstOrNull { it.id == cellId }
      return cell?.index
  }

  /**
   * Fallback method if the session history does not find a cell match.
   * Instead, we rely only on the execution count in the ipynb JSON structure
   * and then do rough comparison of the cell content to see if it is long enough
   * to be a possible match. For cases where the file has been run once from top
   * to bottom, this will be correct.
   *
   * It can be inaccurate if a file session was restarted multiple times with
   * different cells being executed each time. This can result in multiple cells
   * having the same `execution_count`, but the chance is fairly low for this type
   * of behavior, so we accept that risk.
   */
  private fun suggestCellIndex(): Int? {
    val supportedCells = notebook.computeCells().filter { it.executionCount == executionCount }
    val enoughLongCells = supportedCells.filter {
      val index = it.index ?: return@filter false
      val pointer = cellLines.intervals[index]
      cellLine <= pointer.lines.last - pointer.lines.first
    }
    return enoughLongCells.firstOrNull()?.index
  }
}
