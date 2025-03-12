// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenActionsUtil

class CreateKotlinNotebookSingleAction : AnAction(
    KotlinNotebookBundle.message("action.new.notebook.from.template.single.text")
) {
    override fun actionPerformed(e: AnActionEvent) {
        val options = showNewNotebookDialog() ?: return
        val myAction = CreateKotlinNotebookAndOpenProjectAction(options)
        myAction.actionPerformed(e)
    }

    override fun createTemplatePresentation(): Presentation {
        return super.createTemplatePresentation().apply {
            putClientProperty(
                ActionUtil.COMPONENT_PROVIDER,
                WelcomeScreenActionsUtil.createToolbarTextButtonAction(this@CreateKotlinNotebookSingleAction)
            )
        }
    }
}
