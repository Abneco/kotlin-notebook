// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.settings.ui

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.extensions.NotebookCompilerPluginSettingsBuilder
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookSettingsPanelBuilder
import com.intellij.kotlin.jupyter.k2.scriptingSupport.fir.NotebookAnalysisBundledCompilerPlugins
import com.intellij.kotlin.jupyter.k2.settings.KotlinNotebookK2ProjectOptionsProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.MultiSelectComboBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.toMutableProperty

internal class CompilerPluginsSettingsBuilderK2 : NotebookCompilerPluginSettingsBuilder {
    override fun buildCompilerPluginsOptionsSelector(project: Project, settingsPanel: Panel): Row {
        val optionsProvider = KotlinNotebookK2ProjectOptionsProvider.getInstance(project)

        return with(settingsPanel) {
            group(KotlinNotebookBundle.message("kotlin.notebook.settings.analysis.compiler.plugins.title")) {
                row(KotlinNotebookBundle.message("kotlin.notebook.settings.analysis.compiler.plugins.bundled.title")) {
                    addBundledCompilerPluginsComboBox(optionsProvider)
                        .widthGroup(KotlinNotebookSettingsPanelBuilder.Companion.BUILD_WIDTH_GROUP)
                }
            }
        }
    }

    private fun Row.addBundledCompilerPluginsComboBox(
        optionsProvider: KotlinNotebookK2ProjectOptionsProvider,
    ): Cell<MultiSelectComboBox<*>> {
        val comboBox = buildPluginsComboBox()

        return cell(comboBox).comment(
          KotlinNotebookBundle.message("kotlin.notebook.settings.analysis.compiler.plugins.bundled.hint")
        ).bind(
            { it.selectedItems.toMutableList() },
            { component, value ->
                component.setSelectedItems(value)
            },
            optionsProvider::bundledCompilerPlugins.toMutableProperty()
        )
    }

    private fun buildPluginsComboBox(): MultiSelectComboBox<NotebookAnalysisBundledCompilerPlugins> {
        return MultiSelectComboBox(
          NotebookAnalysisBundledCompilerPlugins.entries,
          { it.visibleName },
        )
    }
}