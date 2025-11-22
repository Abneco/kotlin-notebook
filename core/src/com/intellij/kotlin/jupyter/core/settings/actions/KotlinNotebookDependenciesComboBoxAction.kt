// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.actions

import com.intellij.icons.AllIcons
import com.intellij.jupyter.core.core.impl.actions.NotebookEditorActionBase
import com.intellij.jupyter.core.jupyter.helper.editor
import com.intellij.jupyter.core.jupyter.helper.notebookFile
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.projectModel.showKernelAndModuleJdkAreMatchingWarningIfNeeded
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.kotlin.jupyter.core.settings.getSuitableModules
import com.intellij.kotlin.jupyter.core.settings.notebookDependencies
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBFont
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingConstants

class KotlinNotebookDependenciesComboBoxAction : NotebookEditorActionBase(), CustomComponentAction {
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return createCustomComponentForResultViewToolbar(this, presentation, place)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component: Component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
        val popup: JBPopup = JBPopupFactory.getInstance().createActionGroupPopup(
            /* title = */ null,
            /* actionGroup = */ createPopupActionGroup(e.dataContext),
            /* dataContext = */ e.dataContext,
            /* aid = */ null,
            /* showDisabledActions = */ true,
            /* disposeCallback = */ null,
            /* maxRowCount = */ -1,
            /* preselectCondition = */ { actionGroupItem: AnAction ->
                (actionGroupItem as? SelectDependenciesAction)?.dependencies == e.getCurrentDependencies()
            },
            /* actionPlace = */ null
        )
        popup.showUnderneathOf(component)
    }

    private fun createPopupActionGroup(context: DataContext): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(NoDependenciesAction(
            KotlinNotebookBundle.message("action.KotlinNotebookDependenciesComboBoxAction.NoDependenciesAction.text")
        ))
        actionGroup.add(AllProjectLibrariesAction(
            KotlinNotebookBundle.message("action.KotlinNotebookDependenciesComboBoxAction.AllProjectLibrariesAction.text")
        ))
        val modules = getSuitableModules(context.getData(CommonDataKeys.PROJECT) ?: return actionGroup)
        actionGroup.addSeparator()
        actionGroup.addAll(modules.map { SelectModuleAction(it) })
        return actionGroup
    }

    private fun createCustomComponentForResultViewToolbar(
        action: AnAction,
        presentation: Presentation,
        place: String,
    ): JComponent {
        val button: ActionButtonWithText = object : ActionButtonWithText(
            action, presentation, place,
            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
        ) {
            override fun shallPaintDownArrow() = true
        }
        button.setHorizontalTextAlignment(SwingConstants.LEFT)
        button.font = JBFont.small()
        return button
    }

    override fun update(event: AnActionEvent) {
        actionUpdater.update(this, event) { e ->
            val currentDependencies = e.getCurrentDependencies() ?: return@update
            val (icon, text: @NlsActions.ActionText String) = when (currentDependencies) {
                KotlinNotebookDependencies.AllLibraries -> {
                    null to KotlinNotebookBundle.message("action.KotlinNotebookDependenciesComboBoxAction.AllProjectLibrariesAction.text")
                }
                KotlinNotebookDependencies.None -> {
                    null to KotlinNotebookBundle.message("action.KotlinNotebookDependenciesComboBoxAction.NoDependenciesAction.text")
                }
                is KotlinNotebookDependencies.SingleModule -> {
                    val module = e.project?.let { currentDependencies.findModule(it) }
                    module?.let { ModuleType.get(it).icon } to currentDependencies.moduleName
                }
            }
            e.presentation.icon = icon
            e.presentation.text = text
        }
    }

    private sealed class SelectDependenciesAction(
        @NlsActions.ActionText placeholder: String,
        val dependencies: KotlinNotebookDependencies,
    ) : DumbAwareAction(placeholder) {
        override fun actionPerformed(e: AnActionEvent) {
            val editor = e.editor ?: return
            if (e.getCurrentDependencies() == dependencies) {
                return
            }
            val notebookFile = editor.notebookFileOrNull ?: return
            val project = editor.project ?: e.project ?: return

            promptSessionShutdownIfNeeded(project, notebookFile) { hasActiveSession: Boolean ->
                val notebook = notebookFile.notebookOrNull
                notebook?.notebookDependencies = dependencies
                // If no cells were executed, we won't have a compiler service restart triggerred by JupyterSession
                if (!hasActiveSession) {
                    JupyterCompilerService.getInstance(project).recreateService(notebookFile)
                }

                notebook?.showKernelAndModuleJdkAreMatchingWarningIfNeeded(project)
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            if (e.getCurrentDependencies() == dependencies) {
                e.presentation.icon = AllIcons.Actions.Checked
            }
        }
    }

    private class NoDependenciesAction(@NlsActions.ActionText placeholder: String) : SelectDependenciesAction(
        placeholder = placeholder,
        dependencies = KotlinNotebookDependencies.None,
    )

    private class AllProjectLibrariesAction(@NlsActions.ActionText placeholder: String) : SelectDependenciesAction(
        placeholder = placeholder,
        dependencies = KotlinNotebookDependencies.AllLibraries,
    )

    private class SelectModuleAction(private val module: Module) : SelectDependenciesAction(
        placeholder = module.name,
        dependencies = KotlinNotebookDependencies.SingleModule(module.name),
    ) {
        override fun update(e: AnActionEvent) {
            e.presentation.icon = ModuleType.get(module).icon
            super.update(e)
        }
    }
}

private fun AnActionEvent.getCurrentDependencies(): KotlinNotebookDependencies? {
    val notebookFile = notebookFile
    if (notebookFile == null || !notebookFile.isKotlinNotebook) {
        this.presentation.isEnabledAndVisible = false
        return null
    }

    return notebookFile.notebookOrNull?.notebookDependencies
}
