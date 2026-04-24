package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import org.intellij.lang.annotations.Language

@Suppress("NOTHING_TO_INLINE")
inline fun NotebookEditorUiComponent.addKotlinCell(
  @Language("kotlin") text: String,
) = addCodeCell(text)

@Suppress("NOTHING_TO_INLINE")
inline fun NotebookEditorUiComponent.pasteKotlinToCurrentCell(
  @Language("kotlin") text: String,
) = pasteToCurrentCell(text)
