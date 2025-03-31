// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.projectWizard.settings.NewNotebookOptions
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.kotlin.jupyter.core.statistics.fus.WelcomeScreenIdeEntryType
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
        registerIdeEntry(options)
    }

    private fun registerIdeEntry(options: NewNotebookOptions) {
        val entryType = when (options.notebookMode) {
            NotebookMode.STANDARD -> WelcomeScreenIdeEntryType.CREATE_NEW_NOTEBOOK_IN_FOLDER
            NotebookMode.LIGHT -> WelcomeScreenIdeEntryType.CREATE_NEW_SCRATCH_NOTEBOOK
        }
        KotlinNotebookFeatureUsagesCollector.registerIdeEntryFromKotlinNotebookWelcomeScreen(entryType)
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
