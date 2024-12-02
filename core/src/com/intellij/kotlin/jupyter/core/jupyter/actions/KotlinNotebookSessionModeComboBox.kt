// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.isKernelRunModeSelectionEnabled
import com.intellij.kotlin.jupyter.core.settings.readSettings
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingConstants

class KotlinNotebookSessionModeComboBox : DumbAwareAction(), CustomComponentAction {
    override fun update(e: AnActionEvent) {
        if (!isKernelRunModeSelectionEnabled) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val runMode = e.dataContext.getRunMode()
        if (runMode == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.text = runMode.title
    }

    private fun DataContext.getRunMode(): KotlinNotebookSessionRunMode? {
        val notebookFile = notebookFile
        if (notebookFile?.isKotlinNotebook != true) return null
        val notebook = notebookFile.notebookOrNull ?: return null
        return notebook.readSettings().sessionRunMode
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component: Component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
        val popup: JBPopup = JBPopupFactory.getInstance().createActionGroupPopup(
          /* title = */ null,
          /* actionGroup = */ ActionManager.getInstance().getAction("KotlinNotebookChangeSessionModeActions") as ActionGroup,
          /* dataContext = */ e.dataContext,
          /* selectionAidMethod = */ null,
          /* showDisabledActions = */ true,
          /* actionPlace = */ null
        )
        popup.showUnderneathOf(component)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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