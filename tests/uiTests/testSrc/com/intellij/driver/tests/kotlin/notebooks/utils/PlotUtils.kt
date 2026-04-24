package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent

private val plotSpecRegex = Regex("plotSpec\\s*=\\s*\\{([\\s\\S]*?)&quot;spec_id&quot;")

fun NotebookEditorUiComponent.getNotebookPlotSpec(index: Int): String? {
  val firstPlotSource = notebookPlots.getOrNull(index)?.htmlSource ?: return null
  val plotSpecMatch = plotSpecRegex.find(firstPlotSource) ?: return null
  return plotSpecMatch.groupValues[1]
}

val NotebookEditorUiComponent.firstNotebookPlotSpec: String?
  get() = getNotebookPlotSpec(0)
