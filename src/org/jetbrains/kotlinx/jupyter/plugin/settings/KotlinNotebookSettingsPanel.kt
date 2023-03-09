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
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.execution.ParametersListUtil
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
    private class OptionComponent<T : JComponent>(init: () -> T) {
        val component: T by lazy { init() }
    }

    private class OptionComponentInRow<T: JComponent>(private val init: Row.() -> T) {
        val component: T get() = _component!!
        private var _component: T? = null

        fun createComponent(row: Row): T {
            return _component ?: init(row).also { c -> _component = c }
        }
    }

    private lateinit var panel: DialogPanel
    private val jdkPath = OptionComponent(::initJdkComboBox)
    private val heapMaxLimitInMib = OptionComponentInRow { initHeapMaxSizeLimitField() }
    private val extraJvmArgs = OptionComponentInRow { initExtraJvmArgumentsField() }
    private val shouldBuildProject = OptionComponent(::initShouldBuildCheckBox)
    private val shouldAddProjectLibrariesToClasspath = OptionComponent(::initShouldAddProjectLibrariesToClasspath)
    private val shouldLimitTypeHintsByActiveCell = OptionComponent(::initShouldLimitTypeHintsCheckBox)
    private val shouldShowExecutionCount = OptionComponent(::initShouldShowExecutionCountCheckBox)

    private fun collectState(): KotlinNotebookProjectOptionsProvider.State {
        return KotlinNotebookProjectOptionsProvider.State().also {
            it.jdkPath = jdkPath.component.getSelectedSdk()?.homePath
            it.heapMaxLimitInMib = heapMaxLimitInMib.component.number
            it.extraJvmArguments = ParametersListUtil.parse(extraJvmArgs.component.text)
            it.shouldBuildProject = shouldBuildProject.component.isSelected
            it.shouldAddProjectLibrariesToClasspath = shouldAddProjectLibrariesToClasspath.component.isSelected
            it.shouldLimitTypeHintsByActiveCell = shouldLimitTypeHintsByActiveCell.component.isSelected
            it.shouldShowExecutionCount = shouldShowExecutionCount.component.isSelected
        }
    }

    fun createPanel(): JPanel {
        return panel {
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.build")) {
                row(JupyterKotlinBundle.message("kotlin.jupyter.settings.JDK.path")) {
                    cell(jdkPath.component)
                }
                row(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.max.heap")) {
                    heapMaxLimitInMib.createComponent(this)
                    label(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.max.heap.units"))
                }
                row(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.extra.args")) {
                    extraJvmArgs.createComponent(this)
                }
                row(null) {
                    cell(shouldBuildProject.component)
                }
                row(null) {
                    cell(shouldAddProjectLibrariesToClasspath.component)
                }
            }
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.typeHints")) {
                row(null) {
                    cell(shouldLimitTypeHintsByActiveCell.component)
                }
            }
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.appearance")) {
                row(null) {
                    cell(shouldShowExecutionCount.component)
                }
            }
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

    private fun Row.initHeapMaxSizeLimitField(): JBIntSpinner {
        val cell = spinner(0..99999, 100)
        cell.validationRequestor { callback -> cell.onChanged { callback() } }

        val component = cell.component
        return component.apply {
            number = optionsProvider.state.heapMaxLimitInMib
        }
    }

    private fun Row.initExtraJvmArgumentsField(): ExpandableTextField {
        val cell = expandableTextField()
        cell.columns(48)

        val component = cell.component
        return component.apply {
            setMonospaced(true)
            text = ParametersListUtil.DEFAULT_LINE_JOINER.`fun`(optionsProvider.state.extraJvmArguments)
        }
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