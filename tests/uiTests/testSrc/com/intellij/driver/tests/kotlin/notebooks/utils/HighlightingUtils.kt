package com.intellij.driver.tests.kotlin.notebooks.utils

import com.intellij.driver.sdk.invokeActionWithRetries
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.waitForHighlighting

fun NotebookEditorUiComponent.restartHighlighting() {
  driver.withContext {
    invokeActionWithRetries("RestartKotlinNotebookHighlighting")

    waitForHighlighting()
  }
}
