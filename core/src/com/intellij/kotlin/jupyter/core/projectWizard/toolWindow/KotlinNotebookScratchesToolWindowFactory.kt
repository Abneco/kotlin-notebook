// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.projectWizard.CreateKotlinNotebookInCurrentProjectAction
import com.intellij.kotlin.jupyter.core.projectWizard.common.KotlinNotebookTreeHolder
import com.intellij.kotlin.jupyter.core.projectWizard.kotlinNotebookWelcomeFeaturesEnabled
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout

class KotlinNotebookScratchesToolWindowFactory: ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = KotlinNotebookBundle.message("toolwindow.scratches.title")
        toolWindow.title = KotlinNotebookBundle.message("toolwindow.scratches.title")
        addHeaderActions(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ScratchToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override suspend fun isApplicableAsync(project: Project): Boolean {
        return kotlinNotebookWelcomeFeaturesEnabled
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return kotlinNotebookWelcomeFeaturesEnabled
    }

    private fun addHeaderActions(toolWindow: ToolWindow) {
        toolWindow.setTitleActions(listOf(CreateNotebookActionGroup()))
    }
}

private class CreateNotebookActionGroup: ActionGroup(
    KotlinNotebookBundle.message("action.group.create.new.kotlin.scratch.notebook.text"),
    KotlinNotebookBundle.message("action.group.create.new.kotlin.scratch.notebook.description"),
    AllIcons.General.Add
) {
    private val myActions = run {
        NotebookTemplate.entries
            .map { CreateKotlinNotebookInCurrentProjectAction(it) }
            .toTypedArray()
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        return myActions
    }

    override fun createTemplatePresentation(): Presentation {
        return super.createTemplatePresentation().apply {
            isPopupGroup = true
        }
    }
}

class ScratchToolWindowPanel(project: Project) : SimpleToolWindowPanel(true), Disposable {
    override fun dispose() {
        // Clean up resources if needed
    }
    private val treeComponent = KotlinNotebookTreeHolder() { file ->
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    init {
        layout = BorderLayout()

        // Load files
        treeComponent.updateAsync()

        // Add components to the main panel
        add(treeComponent.createScrollPane(), BorderLayout.CENTER)
    }
}
