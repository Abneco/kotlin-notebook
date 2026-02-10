// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.actions.KotlinNotebookEditorActionBase
import com.intellij.kotlin.jupyter.core.settings.isKernelRunModeSelectionEnabled
import com.intellij.kotlin.jupyter.core.settings.readSettings
import com.intellij.kotlin.jupyter.core.util.getKotlinNotebookJupyterFile
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingConstants

class KotlinNotebookSessionModeComboBox : KotlinNotebookEditorActionBase(), CustomComponentAction {
    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { event ->
            val presentation = event.presentation
            if (!isKernelRunModeSelectionEnabled) {
                presentation.isEnabledAndVisible = false
                return@update
            }

            val runMode = event.getRunMode()
            if (runMode == null) {
                presentation.isEnabledAndVisible = false
                return@update
            }
            presentation.text = runMode.title
        }
    }

    private fun AnActionEvent.getRunMode(): KotlinNotebookSessionRunMode? {
        val notebook = getKotlinNotebookJupyterFile() ?: return null
        return notebook.readSettings().sessionRunMode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component: Component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
        val popup: JBPopup = JBPopupFactory.getInstance().createActionGroupPopup(
            /* title = */ null,
            /* actionGroup = */ ActionManager.getInstance().getAction(KotlinNotebookChangeSessionModeActions.ID) as ActionGroup,
            /* dataContext = */ e.dataContext,
            /* selectionAidMethod = */ null,
            /* showDisabledActions = */ true,
            /* actionPlace = */ null
        )
        popup.showUnderneathOf(component)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return createCustomComponentForResultViewToolbar(this, presentation, place)
    }

    private fun createCustomComponentForResultViewToolbar(
        action: AnAction,
        presentation: Presentation,
        place: String,
    ): JComponent {
        val button: ActionButtonWithText = object : ActionButtonWithText(
            action, presentation, place,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        ) {
            override fun shallPaintDownArrow() = true
        }
        button.setHorizontalTextAlignment(SwingConstants.LEFT)
        button.font = JBFont.small()
        return button
    }
}
