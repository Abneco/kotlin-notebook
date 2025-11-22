// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookConfigurable
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class KotlinNotebookShowPreferencesAction : NotebookEditorActionBase() {
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

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinNotebookConfigurable::class.java)
    }
}
