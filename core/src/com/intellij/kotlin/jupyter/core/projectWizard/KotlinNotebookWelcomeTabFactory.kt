// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.registryFlag
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

val kotlinNotebookWelcomeFeaturesEnabled: Boolean by registryFlag("kotlin.notebook.welcome.features", false)

class KotlinNotebookWelcomeTabFactory: WelcomeTabFactory {
    override fun createWelcomeTabs(ws: WelcomeScreen, parentDisposable: Disposable): List<WelcomeScreenTab?> {
        return listOf(KotlinNotebookWelcomeScreenTab(parentDisposable))
    }

    override fun isApplicable(): Boolean {
        return kotlinNotebookWelcomeFeaturesEnabled
    }
}

internal class KotlinNotebookWelcomeScreenTab(private val parentDisposable: Disposable) : DefaultWelcomeScreenTab(
    KotlinNotebookBundle.message("kotlin.notebook.project.wizard.title")
) {
    private val projectsPanelWrapper: Wrapper = Wrapper().apply {
        background = WelcomeScreenUIManager.getProjectsBackground()
    }
    private val recentProjectsPanel: JComponent = createRecentProjectsPanel()
    private val notificationPanel: JComponent = WelcomeScreenComponentFactory.createNotificationToolbar(parentDisposable)
    private var panelState: PanelState

    init {
        panelState = getCurrentState()
        updateState(panelState)
        val connect = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        connect.subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
            override fun onCloneCanceled() {}
            override fun onCloneFailed() {}
            override fun onCloneSuccess() {}
            override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
                checkState()
            }

            override fun onCloneRemoved() {
                checkState()
            }
        })
        connect.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
            override fun change() {
                checkState()
            }
        })
    }

    override fun buildComponent(): JComponent {
        return panel {
            customizeSpacingConfiguration(EmptySpacingConfiguration()) {
                row {
                    cell(projectsPanelWrapper)
                        .align(Align.FILL)
                }.resizableRow()
                row {
                    cell(notificationPanel)
                        .align(AlignX.RIGHT)
                        .applyToComponent {
                            putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
                        }
                }
            }
        }.apply {
            background = WelcomeScreenUIManager.getProjectsBackground()
        }
    }

    private fun checkState() {
        val currentState = getCurrentState()
        if (currentState == panelState) {
            return
        }
        updateState(currentState)
    }

    private fun updateState(currentPanelState: PanelState) {
        projectsPanelWrapper.setContent(recentProjectsPanel)
        panelState = currentPanelState
        projectsPanelWrapper.repaint()
    }

    override fun updateComponent() {
        // balloonLayout is not initialized at this point
        invokeLater {
            val balloonLayout = WelcomeFrame.getInstance()?.balloonLayout
            if (balloonLayout is WelcomeBalloonLayoutImpl) {
                balloonLayout.locationComponent = notificationPanel
            }
        }
    }

    private fun createRecentProjectsPanel(): JComponent {
        val recentProjectTree = RecentKotlinNotebookPanelComponentFactory.createComponent(
            parentDisposable
        )

        val scrollPane = ScrollPaneFactory.createScrollPane(
            recentProjectTree,
            true
        ).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            isOpaque = false
            viewport.isOpaque = false
            background = WelcomeScreenUIManager.getProjectsBackground()
        }
        val projectsPanel = JBUI.Panels.simplePanel(scrollPane)
            .andTransparent()
            .withBackground(WelcomeScreenUIManager.getProjectsBackground())

        return projectsPanel
    }
}


private enum class PanelState {
    EMPTY, NOT_EMPTY
}

private fun getCurrentState(): PanelState {
    return PanelState.NOT_EMPTY
}
