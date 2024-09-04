// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.actions

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
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.findLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.findModules
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSuitableLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectLibraries
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.jupyter.editor.getJupyterVirtualFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.AbstractAction
import javax.swing.Action
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
            !editor.isKotlinNotebook || e.getJupyterVirtualFile() == null
        ) {
            e.presentation.isEnabledAndVisible = false
        }
        super.update(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getJupyterVirtualFile() ?: return

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
            KotlinNotebookBundle.message("kotlin.jupyter.dialog.select.modules.title", notebookName),
            KotlinNotebookBundle.message("kotlin.jupyter.dialog.select.all.modules.checkbox"),
            "kotlin.jupyter.select.modules.dialog",
            tree.allNodesChecked(),
            {
                KotlinNotebookDependencies.fromModules(tree.getSelectedItems())
            },
            { checked ->
                tree.setChecked(checked)
                tree.repaint()
            }
        )
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
            KotlinNotebookBundle.message("kotlin.jupyter.dialog.select.libraries.title", notebookName),
            KotlinNotebookBundle.message("kotlin.jupyter.dialog.select.all.libraries.checkbox"),
            "kotlin.jupyter.select.libraries.dialog",
            initialLibraries.size == allLibraries.size,
            {
                KotlinNotebookDependencies.fromLibraries(allLibraries.filter { list.isItemSelected(it) })
            },
            { checked ->
                allLibraries.forEach { list.setItemSelected(it, checked) }
                list.repaint()
            }
        )
    }
}

private fun showSelectionDialog(
    component: JComponent,
    initialSettings: KotlinNotebookDependencies,
    @NlsContexts.DialogTitle dialogTitle: String,
    @NlsContexts.Checkbox checkboxText: String,
    dimensionKey: String,
    allCheckBoxesSelected: Boolean,
    getSelectedDependencies: () -> KotlinNotebookDependencies,
    selectAll: (Boolean) -> Unit,
): KotlinNotebookDependencies? {
    component.isEnabled = initialSettings != KotlinNotebookDependencies.All

    val allCheckbox = JBCheckBox(checkboxText)
    allCheckbox.isSelected = initialSettings == KotlinNotebookDependencies.All
    allCheckbox.addActionListener {
        component.isEnabled = !allCheckbox.isSelected
    }
    val selectAllAction = ToggleCheckBoxesAction(!allCheckBoxesSelected, selectAll)
    selectAllAction.addToggleSelectionListener { toggleAllSelected ->
        allCheckbox.isSelected = toggleAllSelected
        component.isEnabled = !toggleAllSelected
    }

    val result = DialogBuilder()
        .title(dialogTitle)
        .centerPanel(
            BorderLayoutPanel(0, 5)
                .addToCenter(ScrollPaneFactory.createScrollPane(component))
                .addToBottom(allCheckbox)
        )
        .addLeftSideAction(selectAllAction)
        .dimensionKey(dimensionKey)
        .showAndGet()

    if (result) {
        return if (allCheckbox.isSelected) KotlinNotebookDependencies.All else getSelectedDependencies()
    }
    return null
}

class ToggleCheckBoxesAction(
    initialState: Boolean = false,
    private val selector: (Boolean) -> Unit
): AbstractAction() {
    private val eventDispatcher = EventDispatcher.create(ToggleAllChangedListener::class.java)

    fun interface ToggleAllChangedListener : EventListener {
        fun selectionChanged(allSelected: Boolean)
    }

    @Synchronized
    fun addToggleSelectionListener(listener: ToggleAllChangedListener) {
        eventDispatcher.addListener(listener)
    }
    private var shouldSelect = initialState

    init {
        updateName()
    }

    override fun actionPerformed(e: ActionEvent?) {
        val selectorValue = shouldSelect
        selector(selectorValue)
        shouldSelect = !shouldSelect
        updateName()
        eventDispatcher.multicaster.selectionChanged(selectorValue)
    }

    private fun updateName() {
        putValue(
            Action.NAME,
            getPresentableActionName()
        )
    }

    private fun getPresentableActionName(): @Nls String {
        return if (shouldSelect) {
            KotlinNotebookBundle.message("kotlin.jupyter.settings.dependencies.select.all")
        } else {
            KotlinNotebookBundle.message("kotlin.jupyter.settings.dependencies.deselect.all")
        }
    }
}
