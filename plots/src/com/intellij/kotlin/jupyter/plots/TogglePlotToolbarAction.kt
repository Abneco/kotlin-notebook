// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.jupyter.helper.contextComponent
import com.intellij.kotlin.jupyter.core.util.firstAncestorOfType
import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.openapi.actionSystem.AnActionEvent

class TogglePlotToolbarAction : NotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val component = event.findLetsPlotComponent() ?: return
        component.toggleToolbar()
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) {
            val component = event.findLetsPlotComponent() ?: return@update
            val textKey = if (component.showToolbar) {
                "action.TogglePlotToolbar.text.hide"
            } else {
                "action.TogglePlotToolbar.text.show"
            }
            event.presentation.text = KotlinNotebookPlotsBundle.message(textKey)
        }
    }
}

private fun AnActionEvent.findLetsPlotComponent(): LetsPlotComponent? {
    return contextComponent?.firstAncestorOfType<LetsPlotComponent>()
}
