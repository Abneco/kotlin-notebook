// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptionsProvider

class KotlinNotebookToggleShowExecutionCountAction : ToggleAction(), DumbAware {
    override fun isSelected(e: AnActionEvent): Boolean {
        return service<KotlinNotebookApplicationOptionsProvider>().shouldShowExecutionCount
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        service<KotlinNotebookApplicationOptionsProvider>().shouldShowExecutionCount = state
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (e.project == null || editor == null || !editor.isKotlinNotebook) {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}