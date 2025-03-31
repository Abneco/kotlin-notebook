// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.registryFlag
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.WelcomeScreenTab
import com.intellij.openapi.wm.WelcomeTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.TabbedWelcomeScreen.DefaultWelcomeScreenTab
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

val kotlinNotebookWelcomeFeaturesEnabled: Boolean by registryFlag("kotlin.notebook.welcome.features", true)

class KotlinNotebookWelcomeTabFactory: WelcomeTabFactory {
    override fun createWelcomeTabs(ws: WelcomeScreen, parentDisposable: Disposable): List<WelcomeScreenTab?> {
        return listOf(KotlinNotebookWelcomeScreenTab(parentDisposable))
    }

    override fun isApplicable(): Boolean {
        return kotlinNotebookWelcomeFeaturesEnabled
    }
}

internal class KotlinNotebookWelcomeScreenTab(parentDisposable: Disposable) : DefaultWelcomeScreenTab(
    KotlinNotebookBundle.message("kotlin.notebook.welcome.tab.title")
) {
    private val projectsPanelWrapper: Wrapper = Wrapper().apply {
        background = WelcomeScreenUIManager.getProjectsBackground()
    }
    private val recentProjectsPanel: JComponent = createRecentProjectsPanel()
    private val notificationPanel: JComponent = WelcomeScreenComponentFactory.createNotificationToolbar(parentDisposable)

    init {
        updateState()
    }

    override fun buildComponent(): JComponent {
        KotlinNotebookFeatureUsagesCollector.registerWelcomeScreenTabOpened()
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

    private fun updateState() {
        projectsPanelWrapper.setContent(recentProjectsPanel)
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
        val recentProjectsPanel = RecentKotlinNotebookPanel()
        val projectsPanel = JBUI.Panels.simplePanel(recentProjectsPanel)
            .andTransparent()
            .withBackground(WelcomeScreenUIManager.getProjectsBackground())

        return projectsPanel
    }
}
