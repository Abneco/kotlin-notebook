// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Base class for custom Editor actions which
 * are specific to Kotlin Notebook.
 */
abstract class KotlinNotebookEditorActionBase : NotebookEditorActionBase() {
    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { e ->
            val presentation = e.presentation
            if (e.notebookFile?.isKotlinNotebook != true) {
                presentation.isEnabledAndVisible = false
                return@update
            }
            if (e.project == null) {
                presentation.isEnabled = false
            }
        }
    }
}
