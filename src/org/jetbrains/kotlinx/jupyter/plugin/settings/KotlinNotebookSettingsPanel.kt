// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.editor.JupyterNotebookGutterManager

object KotlinNotebookSettingsPanel {
    fun createPanel(
        project: Project, projectOptions: KotlinNotebookProjectOptionsProvider,
        applicationOptions: KotlinNotebookApplicationOptionsProvider, parentDisposable: Disposable
    ): DialogPanel {
        return panel {
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.build")) {
                createJdkComboBox(project, projectOptions.state, parentDisposable)
                createMaxHeapSizeSpinner(projectOptions.state)
                createExtraJvmArgumentsField(projectOptions.state)
                row {
                    checkBox(JupyterKotlinBundle.message("checkbox.should.build.project"))
                        .bindSelected(projectOptions.state::shouldBuildProject)
                }
                row {
                    checkBox(JupyterKotlinBundle.message("checkbox.should.add.libraries"))
                        .bindSelected(projectOptions.state::shouldAddProjectLibrariesToClasspath)
                }
            }
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.typeHints")) {
                row(null) {
                    checkBox(JupyterKotlinBundle.message("checkbox.should.typehint.only.active.cell"))
                        .bindSelected(projectOptions.state::shouldLimitTypeHintsByActiveCell)
                }
            }
            group(JupyterKotlinBundle.message("kotlin.jupyter.settings.appearance")) {
                row(null) {
                    checkBox(JupyterKotlinBundle.message("checkbox.should.show.execution.count"))
                        .bindSelected(applicationOptions.state::shouldShowExecutionCount)
                        .onApply { refreshEditors() }
                }
            }
        }
    }

    private fun Panel.createMaxHeapSizeSpinner(state: KotlinNotebookProjectOptionsProvider.State): Row {
        return row(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.max.heap")) {
            spinner(0..99999, 100)
                .bindIntValue(state::heapMaxLimitInMib)
                .also {
                    it.validationRequestor { callback -> it.onChanged { callback() } }
                }
            label(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.max.heap.units"))
        }
    }

    private fun Panel.createExtraJvmArgumentsField(state: KotlinNotebookProjectOptionsProvider.State): Row {
        return row(JupyterKotlinBundle.message("kotlin.jupyter.settings.jvm.extra.args")) {
            expandableTextField()
                .columns(48)
                .applyToComponent {
                    setMonospaced(true)
                }
                .bindText(
                    { ParametersListUtil.DEFAULT_LINE_JOINER.`fun`(state.extraJvmArguments) },
                    { text -> state.extraJvmArguments = ParametersListUtil.parse(text) }
                )
        }
    }

    private fun Panel.createJdkComboBox(project: Project, state: KotlinNotebookProjectOptionsProvider.State, disposable: Disposable): Row {
        return row(JupyterKotlinBundle.message("kotlin.jupyter.settings.JDK.path")) {
            val sdkComboBox = SdkComboBox(
                SdkComboBoxModel.createProjectJdkComboBoxModel(
                    project, disposable,
                    sdkFilter = ::isSuitableForStartingKernel
                )
            )
            val sdkModel = sdkComboBox.model.sdksModel
            cell(sdkComboBox)
                .onReset {
                    val jdkName = state.jdkName
                    if (jdkName != null) {
                        sdkComboBox.setSelectedSdk(jdkName)
                    } else {
                        sdkComboBox.selectedItem = SdkListItem.ProjectSdkItem()
                    }
                }
                .onIsModified {
                    sdkModel.isModified || state.jdkName != sdkComboBox.selectedSdkName
                }
                .onApply {
                    if (sdkModel.isModified) {
                        sdkModel.apply()
                    }
                    state.jdkName = sdkComboBox.selectedSdkName
                }
        }
    }

    private val SdkComboBox.selectedSdkName: String?
        get() {
            if (selectedItem is SdkListItem.ProjectSdkItem) return null
            return getSelectedSdk()?.name
        }

    private fun refreshEditors() {
        EditorFactory.getInstance().allEditors.forEach {
            if (it.isKotlinNotebook) {
                JupyterNotebookGutterManager.putHighlighters(it as EditorEx)
                it.component.repaint()
            }
        }
    }
}