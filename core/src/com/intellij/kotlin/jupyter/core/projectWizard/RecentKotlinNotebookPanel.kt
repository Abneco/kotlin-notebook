package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.icons.AllIcons
import com.intellij.kotlin.jupyter.core.projectWizard.common.KotlinNotebookTreeHolder
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.util.function.Supplier

class RecentKotlinNotebookPanel(): BorderLayoutPanel() {
    init {
        KotlinNotebookPluginScope.global.launch {
            withContext(Dispatchers.EDT) {
                initialize()
            }
        }
    }

    suspend fun initialize() {
        withBorder(JBUI.Borders.empty(13, 12))
        withBackground(WelcomeScreenUIManager.getProjectsBackground())

        val treeComponent = KotlinNotebookTreeHolder()
        treeComponent.updateAsync().join()
        val filteringTree = RecentKotlinNotebookFilteringTree(treeComponent)
        filteringTree.updateAsync().join()

        val northPanel = JBUI.Panels.simplePanel()
            .andTransparent()
            .withBorder(object : CustomLineBorder(WelcomeScreenUIManager.getSeparatorColor(), JBUI.insetsBottom(1)) {
                override fun getBorderInsets(c: Component): Insets {
                    return JBUI.insetsBottom(12)
                }
            })

        val searchField = filteringTree.installSearchField()
        if (ExperimentalUI.isNewUI()) {
            searchField.textEditor.putClientProperty("JTextField.Search.Icon", AllIcons.Actions.Search)
        }

        val openAction = OpenKotlinNotebookAction()
        val createAction = CreateKotlinNotebookSingleAction()
        val group = DefaultActionGroup(openAction, createAction)
        val toolbar = object : ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, group, true) {
            override fun createToolbarButton(
                action: AnAction,
                look: ActionButtonLook?,
                place: String,
                presentation: Presentation,
                minimumSize: Supplier<out Dimension>
            ): ActionButton {
                presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
                return super.createToolbarButton(action, look, place, presentation, minimumSize)
            }
        }.apply {
            isOpaque = false
            setTargetComponent(searchField)
        }

        northPanel.add(searchField, BorderLayout.CENTER)
        northPanel.add(toolbar.component, BorderLayout.EAST)

        val projectsPanel = JBUI.Panels.simplePanel(treeComponent.createScrollPane())
            .andTransparent()
            .withBorder(JBUI.Borders.emptyTop(10))
            .withBackground(WelcomeScreenUIManager.getProjectsBackground())

        add(northPanel, BorderLayout.NORTH)
        add(projectsPanel, BorderLayout.CENTER)
    }
}
