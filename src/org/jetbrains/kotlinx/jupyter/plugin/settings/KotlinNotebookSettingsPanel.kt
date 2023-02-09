// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.editor.JupyterNotebookGutterManager
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class KotlinNotebookSettingsPanel(
    private val project: Project,
    private val optionsProvider: KotlinNotebookProjectOptionsProvider
) {
    private class OptionComponentInitializer<T: JComponent>(
        init: () -> T,
        val setupUI: Panel.(OptionComponentInitializer<*>) -> Unit
    ) {
        val component: T by lazy { init() }
    }

    private lateinit var panel: DialogPanel
    private val jdkPath = OptionComponentInitializer(::initJdkComboBox) {
        row(JupyterKotlinBundle.message("kotlin.jupyter.settings.JDK.path")) {
            cell(it.component)
        }
    }
    private val shouldBuildProject = OptionComponentInitializer(::initShouldBuildCheckBox) {
        row(null) {
            cell(it.component)
        }
    }
    private val shouldAddProjectLibrariesToClasspath = OptionComponentInitializer(::initShouldAddProjectLibrariesToClasspath) {
        row(null) {
            cell(it.component)
        }
    }
    private val shouldLimitTypeHintsByActiveCell = OptionComponentInitializer(::initShouldLimitTypeHintsCheckBox) {
        this.group(JupyterKotlinBundle.message("kotlin.jupyter.settings.typeHints")) {
            row(null) {
                cell(it.component)
            }
        }
    }
    private val shouldShowExecutionCount = OptionComponentInitializer(::initShouldShowExecutionCountCheckBox) {
        this.group(JupyterKotlinBundle.message("kotlin.jupyter.settings.appearance")) {
            row(null) {
                cell(it.component)
            }
        }
    }
    private val providerInitializers = listOf(
        jdkPath,
        shouldBuildProject,
        shouldAddProjectLibrariesToClasspath,
        shouldLimitTypeHintsByActiveCell,
        shouldShowExecutionCount
    )

    private fun collectState(): KotlinNotebookProjectOptionsProvider.State {
        val path = jdkPath.component.getSelectedSdk()?.homePath
        val jdk = if (path == null) ProjectJdkOption else JdkOptionWithPath(path)
        return KotlinNotebookProjectOptionsProvider.State(
            jdk = jdk,
            shouldBuildProject = shouldBuildProject.component.isSelected,
            shouldAddProjectLibrariesToClasspath = shouldAddProjectLibrariesToClasspath.component.isSelected,
            shouldLimitTypeHintsByActiveCell = shouldLimitTypeHintsByActiveCell.component.isSelected,
            shouldShowExecutionCount = shouldShowExecutionCount.component.isSelected
        )
    }

    fun createPanel(): JPanel {
        return panel {
            providerInitializers.forEach { it.setupUI(this, it) }
        }.also { panel = it }
    }

    private fun initShouldBuildCheckBox(): JCheckBox {
        return JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.build.project"),
            optionsProvider.state.shouldBuildProject
        )
    }

    private fun initShouldAddProjectLibrariesToClasspath(): JCheckBox {
        return JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.add.libraries"),
            optionsProvider.state.shouldAddProjectLibrariesToClasspath
        )
    }

    private fun initShouldLimitTypeHintsCheckBox(): JCheckBox {
        return JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.typehint.only.active.cell"),
            optionsProvider.state.shouldLimitTypeHintsByActiveCell
        )
    }

    private fun initShouldShowExecutionCountCheckBox(): JCheckBox {
        return JCheckBox(
            JupyterKotlinBundle.message("checkbox.should.show.execution.count"),
            optionsProvider.state.shouldShowExecutionCount
        )
    }

    private fun initJdkComboBox(): SdkComboBox {
        val comboBoxModel = SdkComboBoxModel.createProjectJdkComboBoxModel(
            project,
            KotlinNotebookProjectOptionsProvider.getInstance(project),
            sdkFilter = ::isSuitableForStartingKernel
        )
        val comboBox = SdkComboBox(comboBoxModel)
        val projectItem = comboBox.showProjectSdkItem()
        val jdk = optionsProvider.state.jdk
        val jdkPath = jdk.getPath(project)
        if (jdkPath != null) {
            val jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())
            val sdk: Sdk? = jdks.firstOrNull { it.homePath == jdkPath }
            if (sdk != null) {
                comboBox.setSelectedSdk(sdk)
            }
        } else if (jdk is ProjectJdkOption) {
            comboBox.selectedItem = projectItem
        }
        return comboBox
    }

    fun apply() {
        val oldState = optionsProvider.state
        val newState = collectState()
        optionsProvider.loadState(newState)

        if (oldState.shouldShowExecutionCount != newState.shouldShowExecutionCount) {
            refreshEditors()
        }
    }

    private fun refreshEditors() {
        EditorFactory.getInstance().allEditors.forEach {
            if (it.isKotlinNotebook) {
                JupyterNotebookGutterManager.putHighlighters(it as EditorEx)
                it.component.repaint()
            }
        }
    }

    fun isModified(): Boolean {
        return !collectState().equals(optionsProvider.state, project)
    }
}