// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

class KotlinNotebookSettingsActions : DefaultActionGroup(), DumbAware {
    init {
        templatePresentation.isHideGroupIfEmpty = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        super.update(e)

        if (e.isFromSearchPopup) {
            e.presentation.isVisible = false
            return
        }
    }
}
