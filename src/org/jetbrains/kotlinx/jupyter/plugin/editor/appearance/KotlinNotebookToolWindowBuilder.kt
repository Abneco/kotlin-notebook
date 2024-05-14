// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.debugFeaturesEnabled
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.StopKotlinKernelAction
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowRunMode
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.util.fileNameFromProjectRoot
import org.jetbrains.kotlinx.jupyter.plugin.variables.NotebookVariablesToolWindowSetup
import org.jetbrains.kotlinx.jupyter.plugin.variables.NotebookVarsToolWindowProvider
import org.jetbrains.plugins.notebooks.core.api.NotebookDisposable

/**
 * Class responsible for setting up the UI elements making up the Kotlin Notebook tool window
 * that is found on the toolbar.
 */
class KotlinNotebookToolWindowBuilder(
  private val mode: KotlinNotebookToolWindowRunMode,
  private val id: String,
) {
    companion object {
        val logContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.log")
        val variableContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.variables")
    }
    @NlsSafe
    val windowTitle = mode.notebookPath.fileNameFromProjectRoot(mode.project)
    private val virtualFile = mode.notebookVirtualFile()

    private val kernelContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.title", windowTitle)

    private val ui = RunnerLayoutUi.Factory.getInstance(mode.project)
        .create(
            id,
            kernelContentTitle,
            kernelContentTitle,
            NotebookDisposable.forProject(mode.project)
        )

    fun createMainContent(): Content {
        getAllContent().forEach {
            it.isCloseable = false
            ui.addContent(it)
        }
        initializeLeftToolBar()

        val mainContent = ContentFactory.getInstance().createContent(
            ui.component,
            windowTitle,
            true
        )
        ui.contents.forEach {
            Disposer.register(mainContent, it)
        }
        mainContent.isCloseable = false
        mainContent.helpId = id

        return mainContent
    }

    private fun initializeLeftToolBar() {
        val group = DefaultActionGroup(StopKotlinKernelAction(mode.handler))
        ui.options.setLeftToolbar(group, ActionPlaces.TOOLBAR)
    }

    private fun getAllContent(): Collection<Content> {
        return listOfNotNull(
            createConsoleView(),
            createVariablesView()
        )
    }

    private fun createConsoleView(): Content {
        val console = ConsoleViewImpl(mode.project, GlobalSearchScope.allScope(mode.project), true, true)
        mode.consoleWindowCreated(console)
        val consoleContent = ui.createContent(
            id + "Console",
            console.component,
            logContentTitle,
            null,
            console.getPreferredFocusedComponent()
        )

        return consoleContent
    }

    private fun createVariablesView(): Content? {
        val enabled = debugFeaturesEnabled && mode.shouldShowVariablesView()
        val setupData = NotebookVariablesToolWindowSetup(
            ui, id, variableContentTitle, enabled
        )

        /**
         * Always register the tool window, so [NotebookVarsToolWindowProvider]
         * always have something to return. This prevents it from creating its
         * own tab in the tool window.
         */
        val toolWindowPanel = KotlinNotebookSessionVariablesService
            .getForFile(mode.project, virtualFile)
            .getToolWindow(setupData)

        return if (enabled) {
            toolWindowPanel.createContent()
        } else {
            null
        }
    }
}
