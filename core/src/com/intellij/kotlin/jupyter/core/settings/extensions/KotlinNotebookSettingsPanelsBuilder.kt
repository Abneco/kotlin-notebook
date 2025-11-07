// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.extensions

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row

/**
 * Provides UI settings components for various UI panels
 * inside [KotlinNotebookConfigurable] based on current Kotlin plugin mode.
 *
 * Methods would be called as a part of [KotlinNotebookConfigurable.createPanel]
 */
interface KotlinNotebookSettingsPanelsBuilder : KotlinPluginModeAwareHandler {
    /**
     * Main entry point to create additional options under the 'JVM and Build' section.
     */
    fun buildJvmAndBuildOptionsSelector(project: Project, settingsPanel: Panel, componentDisposable: Disposable): Row? = null

    /**
     * Main entry point to create additional options inside Kotlin Notebook settings.
     */
    fun buildAdditionalOptionsPanel(project: Project, settingsPanel: Panel, componentDisposable: Disposable): Row? = null

    companion object {
        private val EP: ExtensionPointName<KotlinNotebookSettingsPanelsBuilder> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.settings.settingsPanelBuilder")

        fun Project.buildAdditionalJvmAndBuildOptions(settingsPanel: Panel, parentDisposable: Disposable): List<Row> {
            val extensions = EP.extensionList
            return extensions.mapNotNull { builder ->
                builder.buildJvmAndBuildOptionsSelector(this, settingsPanel, parentDisposable)
            }
        }

        fun Project.buildAdditionalOptionPanels(settingsPanel: Panel, parentDisposable: Disposable): List<Row> {
            val extensions = EP.extensionList
            return extensions.mapNotNull { builder ->
                builder.buildAdditionalOptionsPanel(this, settingsPanel, parentDisposable)
            }
        }
    }
}