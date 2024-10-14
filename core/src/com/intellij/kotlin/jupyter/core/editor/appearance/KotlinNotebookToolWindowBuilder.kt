// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.kotlin.jupyter.core.debug.util.debugFeaturesEnabled
import com.intellij.kotlin.jupyter.core.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.kotlin.jupyter.core.jupyter.actions.StopKotlinKernelAction
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowSettings
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.addNotebookTabsContent
import com.intellij.kotlin.jupyter.core.util.findEditors
import com.intellij.kotlin.jupyter.core.util.toPresentablePathAsTabTitle
import com.intellij.kotlin.jupyter.core.variables.NotebookVariablesToolWindowSetup
import com.intellij.kotlin.jupyter.core.variables.NotebookVarsToolWindowProvider
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.readAction
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Class responsible for setting up the UI elements making up the Kotlin Notebook tool window
 * that is found on the toolbar.
 */
class KotlinNotebookToolWindowBuilder(
    bgtScope: CoroutineScope,
    private val settings: KotlinNotebookToolWindowSettings,
    private val panelHelpId: String,
    contentManager: ContentManager
) {
    private val virtualFile = settings.notebookVirtualFile()

    private val windowTitle: Deferred<@NlsSafe String> = bgtScope.async {
        readAction {
            virtualFile.toPresentablePathAsTabTitle(settings.project, contentManager)
        }
    }

    private val kernelContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.title", windowTitle)

    private val ui = RunnerLayoutUi.Factory.getInstance(settings.project)
        .create(
            panelHelpId,
            kernelContentTitle,
            kernelContentTitle,
            settings.handler
        )

    suspend fun createMainContent(): Content {
        ui.addNotebookTabsContent(
            createConsoleView(),
            createVariablesView()
        )
        initializeLeftToolBar()

        val mainContent = ContentFactory.getInstance().createContent(
            ui.component,
            windowTitle.await(),
            true
        )
        for (childContent in ui.contents) {
            Disposer.register(mainContent, childContent)
        }

        mainContent.isCloseable = false
        mainContent.helpId = panelHelpId

        return mainContent
    }

    private fun initializeLeftToolBar() {
        val project = settings.project
        val virtualFile = settings.notebookVirtualFile().file
        val virtualFiles = listOf(virtualFile)
        val editors = project.findEditors(virtualFile)

        val stopAction = StopKotlinKernelAction(
            project,
            virtualFiles,
            editors,
            settings.handler
        )

        val group = DefaultActionGroup(stopAction)
        ui.options.setLeftToolbar(group, ActionPlaces.TOOLBAR)
    }

    private fun createConsoleView(): Content {
        val console = ConsoleViewImpl(settings.project, GlobalSearchScope.allScope(settings.project), true, true)
        settings.consoleWindowCreated(console)
        val consoleContent = ui.createContent(
            panelHelpId + "Console",
            console.component,
            KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.log"),
            null,
            console.getPreferredFocusedComponent()
        )

        return consoleContent
    }

    private fun createVariablesView(): Content? {
        val enabled = debugFeaturesEnabled && settings.shouldShowVariablesView()
        val setupData = NotebookVariablesToolWindowSetup(
            ui, panelHelpId,
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

        return if (enabled) {
            toolWindowPanel.createContent()
        } else {
            null
        }
    }
}
