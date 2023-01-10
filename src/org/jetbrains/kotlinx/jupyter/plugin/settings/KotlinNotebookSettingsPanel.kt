// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class KotlinNotebookSettingsPanel(
    private val project: Project,
    private val optionsProvider: KotlinNotebookProjectOptionsProvider
) {
    private lateinit var panel: DialogPanel
    private lateinit var jdkPath: SdkComboBox
    private lateinit var shouldBuildProject: JCheckBox
    private lateinit var shouldLimitTypeHintsByActiveCell: JCheckBox
    private val initOrder = listOf(
        ::initJdkComboBox,
        ::initShouldBuildCheckBox,
        ::initShouldLimitTypeHintsCheckBox
    )

    private fun collectState(): KotlinNotebookProjectOptionsProvider.State {
        return KotlinNotebookProjectOptionsProvider.State(
            jdkPath = jdkPath.getSelectedSdk()?.homePath,
            shouldBuildProject = shouldBuildProject.isSelected,
            shouldLimitTypeHintsByActiveCell = shouldLimitTypeHintsByActiveCell.isSelected
        )
    }

    fun createPanel(): JPanel {
        initOrder.forEach { it.invoke() }

        return panel {
            row(JupyterKotlinBundle.message("kotlin.jupyter.settings.JDK.path")) {
                cell(jdkPath)
            }
            row(null) {
                cell(shouldBuildProject)
            }
            this.group(JupyterKotlinBundle.message("kotlin.jupyter.settings.typeHints")) {
                row(null) {
                    cell(shouldLimitTypeHintsByActiveCell)
                }
            }
        }.also { panel = it }
    }

    private fun initShouldBuildCheckBox() {
        shouldBuildProject = JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.build.project"),
            optionsProvider.state.shouldBuildProject
        )
    }

    private fun initShouldLimitTypeHintsCheckBox() {
        shouldLimitTypeHintsByActiveCell = JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.typehint.only.active.cell"),
            optionsProvider.state.shouldLimitTypeHintsByActiveCell
        )
    }

    private fun initJdkComboBox() {
        val comboBoxModel = SdkComboBoxModel.createProjectJdkComboBoxModel(
            project,
            KotlinNotebookProjectOptionsProvider.getInstance(project),
        )
        jdkPath = SdkComboBox(comboBoxModel)
    }

    fun apply() {
        optionsProvider.loadState(collectState())
    }

    fun isModified(): Boolean {
        return collectState() != optionsProvider.state
    }
}