// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class KotlinNotebookShowPreferencesAction : KotlinNotebookEditorActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinNotebookConfigurable::class.java)
    }
}
