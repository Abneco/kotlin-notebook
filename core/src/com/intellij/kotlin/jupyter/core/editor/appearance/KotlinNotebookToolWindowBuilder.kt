// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowBuilder
import com.intellij.jupyter.execution.toolwindow.KernelRunnableToolWindowSettings
import com.intellij.kotlin.jupyter.core.debug.util.debugFeaturesEnabled
import com.intellij.kotlin.jupyter.core.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.kotlin.jupyter.core.jupyter.actions.StopKotlinKernelAction
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.toNotebookToolWindowPanelHelpId
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.findEditors
import com.intellij.kotlin.jupyter.core.util.toPresentablePathAsTabTitle
import com.intellij.kotlin.jupyter.core.variables.NotebookVariablesToolWindowSetup
import com.intellij.kotlin.jupyter.core.variables.NotebookVarsToolWindowProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.readAction
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager

/**
 * Class responsible for setting up the UI elements making up the Kotlin Notebook tool window
 * that is found on the toolbar.
 */
class KotlinNotebookToolWindowBuilder private constructor(
    settings: KernelRunnableToolWindowSettings,
    title: String
) : KernelProcessToolWindowBuilder(
    settings,
    KotlinNotebookBundle.message("kotlin.jupyter.toolbar.title", title),
    settings.notebookPath.toNotebookToolWindowPanelHelpId()
) {
    private val virtualFile = settings.notebookVirtualFile()

    override fun collectContentTabs(): List<Content> = buildList {
        add(createConsoleView())
        createVariablesView()?.let { add(it) }
    }

    override fun leftToolbarActions(): ActionGroup {
        val virtualFile = settings.notebookVirtualFile().file
        val editors = settings.project.findEditors(virtualFile)

        val stopAction = StopKotlinKernelAction(
            settings.project,
            listOf(virtualFile),
            editors,
            settings.handler
        )

        return DefaultActionGroup(stopAction)
    }

    private fun createConsoleView(): Content {
        return createConsoleContent(KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.log"))
    }

    private fun createVariablesView(): Content? {
        val enabled = debugFeaturesEnabled && settings.shouldShowVariablesView()
        val setupData = NotebookVariablesToolWindowSetup(
            ui, settings.notebookPath.toNotebookToolWindowPanelHelpId(),
            KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.variables"),
            enabled
        )

        /**
         * Always register the tool window, so [NotebookVarsToolWindowProvider]
         * always have something to return. This prevents it from creating its
         * own tab in the tool window.
         */
        val toolWindowPanel = KotlinNotebookSessionVariablesService
            .getForFile(settings.project, virtualFile)
            .getToolWindow(setupData)

        return if (enabled) toolWindowPanel.createContent() else null
    }

    companion object {
        suspend fun create(
            settings: KernelRunnableToolWindowSettings,
            contentManager: ContentManager
        ): KotlinNotebookToolWindowBuilder {
            val title = readAction {
                settings.notebookVirtualFile().toPresentablePathAsTabTitle(settings.project, contentManager)
            }
            return KotlinNotebookToolWindowBuilder(settings, title)
        }
    }
}