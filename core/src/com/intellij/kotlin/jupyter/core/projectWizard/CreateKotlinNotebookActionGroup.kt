// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.ACTION_GROUP_POPUP_CAPTION
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil

class CreateKotlinNotebookActionGroup : DefaultActionGroup(KotlinNotebookBundle.message("action.new.notebook.from.template.group.text"), true) {
    private val myActions = run {
        val emptyTemplateAction = CreateKotlinNotebookAndOpenProjectAction(NotebookTemplate.EMPTY)
        val otherActions = NotebookTemplate.entries
            .filter { it != NotebookTemplate.EMPTY }
            .map { CreateKotlinNotebookAndOpenProjectAction(it) }
            .toTypedArray()

        val separator = Separator()

        arrayOf(
            emptyTemplateAction,
            separator,
            *otherActions
        )
    }

    override fun createTemplatePresentation(): Presentation {
        return super.createTemplatePresentation().apply {
            putClientProperty(ACTION_GROUP_POPUP_CAPTION, ActionUtil.ActionGroupPopupCaption.NONE)
            putClientProperty(
                ActionUtil.COMPONENT_PROVIDER, WelcomeScreenActionsUtil.createToolbarTextButtonAction(this@CreateKotlinNotebookActionGroup))
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        return myActions
    }
}