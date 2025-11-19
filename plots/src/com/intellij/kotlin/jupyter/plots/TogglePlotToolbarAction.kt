// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.kotlin.jupyter.core.util.firstAncestorOfType
import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys

class TogglePlotToolbarAction : NotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val component: LetsPlotComponent = contextComponent?.firstAncestorOfType<LetsPlotComponent>() ?: return

        component.toggleToolbar()
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) {
            val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
            val component: LetsPlotComponent = contextComponent?.firstAncestorOfType<LetsPlotComponent>() ?: return@update

            val textKey = if (component.showToolbar) {
                "action.TogglePlotToolbar.text.disable"
            } else {
                "action.TogglePlotToolbar.text.enable"
            }
            event.presentation.text = KotlinNotebookPlotsBundle.message(textKey)
        }
    }
}
