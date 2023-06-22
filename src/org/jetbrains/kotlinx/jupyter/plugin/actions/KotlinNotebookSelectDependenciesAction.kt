// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.CheckBoxList
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.buildModuleTree
import org.jetbrains.kotlinx.jupyter.plugin.settings.findLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.findModules
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSuitableLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedItems
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectLibraries
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.editor.getJupyterVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook
import javax.swing.JComponent
import javax.swing.ListSelectionModel

abstract class KotlinNotebookSelectDependenciesAction : DumbAwareAction() {
    abstract var JupyterNotebook.property: KotlinNotebookDependencies

    abstract fun selectDependencies(
        project: Project,
        initialSettings: KotlinNotebookDependencies,
        notebookName: String
    ): KotlinNotebookDependencies?

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (e.project == null || editor == null ||
            !editor.isKotlinNotebook || getJupyterVirtualFile(e) == null
        ) {
            e.presentation.isEnabledAndVisible = false
        }
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = getJupyterVirtualFile(e) ?: return

        val selection = selectDependencies(project, file.notebook.property, file.file.presentableName)
        if (selection != null) {
            file.notebook.property = selection
        }
    }
}

class KotlinNotebookSelectModulesAction : KotlinNotebookSelectDependenciesAction() {

    override var JupyterNotebook.property: KotlinNotebookDependencies
        get() = projectDependencies
        set(value) {
            projectDependencies = value
        }

    override fun selectDependencies(
        project: Project,
        initialSettings: KotlinNotebookDependencies,
        notebookName: String
    ): KotlinNotebookDependencies? {
        val tree = buildModuleTree(project, initialSettings.findModules(project).toSet())

        return showSelectionDialog(
            tree, initialSettings,
            JupyterKotlinBundle.message("kotlin.jupyter.dialog.select.modules.title", notebookName),
            JupyterKotlinBundle.message("kotlin.jupyter.dialog.select.all.modules.checkbox"),
            "kotlin.jupyter.select.modules.dialog"
        ) {
            KotlinNotebookDependencies.fromModules(tree.getSelectedItems())
        }
    }
}

class KotlinNotebookSelectLibrariesAction : KotlinNotebookSelectDependenciesAction() {

    override var JupyterNotebook.property: KotlinNotebookDependencies
        get() = projectLibraries
        set(value) {
            projectLibraries = value
        }

    override fun selectDependencies(
        project: Project,
        initialSettings: KotlinNotebookDependencies,
        notebookName: String
    ): KotlinNotebookDependencies? {
        val allLibraries = getSuitableLibraries(project)
        val initialLibraries = initialSettings.findLibraries(project).toSet()

        val list = CheckBoxList<Library>()
        list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        list.setItems(allLibraries, Library::getPresentableName)
        initialLibraries.forEach { list.setItemSelected(it, true) }

        return showSelectionDialog(
            list, initialSettings,
            JupyterKotlinBundle.message("kotlin.jupyter.dialog.select.libraries.title", notebookName),
            JupyterKotlinBundle.message("kotlin.jupyter.dialog.select.all.libraries.checkbox"),
            "kotlin.jupyter.select.libraries.dialog"
        ) {
            KotlinNotebookDependencies.fromLibraries(allLibraries.filter { list.isItemSelected(it) })
        }
    }
}

private fun showSelectionDialog(
    component: JComponent,
    initialSettings: KotlinNotebookDependencies,
    @NlsContexts.DialogTitle dialogTitle: String,
    @NlsContexts.Checkbox checkboxText: String,
    dimensionKey: String,
    getSelectedDependencies: () -> KotlinNotebookDependencies
): KotlinNotebookDependencies? {
    component.isEnabled = initialSettings != KotlinNotebookDependencies.All

    val allCheckbox = JBCheckBox(checkboxText)
    allCheckbox.isSelected = initialSettings == KotlinNotebookDependencies.All
    allCheckbox.addActionListener {
        component.isEnabled = !allCheckbox.isSelected
    }

    val result = DialogBuilder()
        .title(dialogTitle)
        .centerPanel(
            BorderLayoutPanel(0, 5)
                .addToCenter(ScrollPaneFactory.createScrollPane(component))
                .addToBottom(allCheckbox)
        )
        .dimensionKey(dimensionKey)
        .showAndGet()

    if (result) {
        return if (allCheckbox.isSelected) KotlinNotebookDependencies.All else getSelectedDependencies()
    }
    return null
}