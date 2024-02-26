// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.appearance

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.getPreferredFocusedComponent
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.StopKotlinKernelAction
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.KotlinKernelProcessHandler
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.util.fileNameFromProjectRoot
import org.jetbrains.kotlinx.jupyter.plugin.variables.KotlinNotebookVarsToolWindow
import org.jetbrains.plugins.notebooks.core.api.NotebookDisposable

class KotlinNotebookToolWindowBuilder(
    private val handler: KotlinKernelProcessHandler,
    private val id: String,
) {
    companion object {
        val logContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.log")
        val variableContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.variables")
    }
    @NlsSafe
    val windowTitle = handler.notebookPath.fileNameFromProjectRoot(handler.project)
    private val virtualFile = handler.notebookVirtualFile

    private val kernelContentTitle = KotlinNotebookBundle.message("kotlin.jupyter.toolbar.title", windowTitle)

    private val ui = RunnerLayoutUi.Factory.getInstance(handler.project)
        .create(
            id, kernelContentTitle, kernelContentTitle, NotebookDisposable.forProject(handler.project)
        )

    fun createMainContent(): Content {
        getAllContent().forEach {
            ui.addContent(it)
        }
        initializeToolBar()

        val mainContent = ContentFactory.getInstance().createContent(
            ui.component,
            windowTitle,
            true
        )
        mainContent.isCloseable = false

        return mainContent
    }

    private fun initializeToolBar() {
        val group = DefaultActionGroup(StopKotlinKernelAction(handler))
        ui.options.setLeftToolbar(group, ActionPlaces.TOOLBAR)
    }

    private fun getAllContent(): Collection<Content> {
        return listOfNotNull(
            createConsoleView(),
            createVariablesView()
        )
    }

    private fun createConsoleView(): Content {
        val console = ConsoleViewImpl(handler.project, GlobalSearchScope.allScope(handler.project), true, true)

        console.attachToProcess(handler)

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
        if (virtualFile == null) return null

        val toolWindowPanel = KotlinNotebookSessionVariablesService
            .getForFile(handler.project, virtualFile)
            .getOrCreateVariablesWindowPanel { panel ->
                ui.createContent(
                    id + variableContentTitle,
                    panel,
                    variableContentTitle,
                    null,
                    panel.getPreferredFocusedComponent()
                )
            } as? KotlinNotebookVarsToolWindow

        if (toolWindowPanel == null) return null

        return toolWindowPanel.panelContent
    }
}