// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.Disposable
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
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.isSuitableForStartingKernel
import org.jetbrains.kotlinx.jupyter.plugin.settings.minJdkVersion

object KotlinNotebookSettingsPanel {
    fun createPanel(
        project: Project, projectOptions: KotlinNotebookProjectOptionsProvider,
        applicationOptions: KotlinNotebookApplicationOptionsProvider, parentDisposable: Disposable
    ): DialogPanel {
        return panel {
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.build")) {
                createKernelVersionSelector(project, projectOptions)
                createJdkComboBox(project, projectOptions, parentDisposable)
                createJvmTargetForSnippetsComboBox(projectOptions)
                createMaxHeapSizeSpinner(projectOptions)
                createExtraJvmArgumentsField(projectOptions)
                createEnvironmentVariablesField(projectOptions)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.typeHints")) {
                row(null) {
                    checkBox(KotlinNotebookBundle.message("checkbox.should.typehint.only.active.cell"))
                        .bindSelected(projectOptions::shouldLimitTypeHintsByActiveCell)
                }
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.appearance")) {
                row(null) {
                    checkBox(KotlinNotebookBundle.message("checkbox.should.show.execution.count"))
                        .bindSelected(applicationOptions::shouldShowExecutionCount)
                }
            }
        }
    }

    private fun Panel.createKernelVersionSelector(project: Project, optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.version")) {
            mavenVersionComboBox(
                project,
                KotlinNotebookMavenArtifacts.KERNEL_SHADOWED,
                optionsProvider::kernelVersion,
                KotlinKernelVersion.STRING_VERSION_COMPARATOR.reversed(),
            )
        }
    }

    private fun Panel.createMaxHeapSizeSpinner(optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.max.heap")) {
            spinner(0..99999, 100)
                .bindIntValue(optionsProvider::heapMaxLimitInMib)
                .also {
                    it.validationRequestor { callback -> it.onChanged { callback() } }
                }
            label(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.max.heap.units"))
        }
    }

    private fun Panel.createExtraJvmArgumentsField(optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.extra.args")) {
            expandableTextField()
                .columns(DEFAULT_COLUMNS_COUNT)
                .widthGroup(BUILD_WIDTH_GROUP)
                .applyToComponent {
                    setMonospaced(true)
                }
                .bindText(
                    { ParametersListUtil.DEFAULT_LINE_JOINER.`fun`(optionsProvider.extraJvmArguments) },
                    { text -> optionsProvider.extraJvmArguments = ParametersListUtil.parse(text) }
                )
        }
    }

    private fun Panel.createEnvironmentVariablesField(optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.environment.variables")) {
            cell(EnvironmentVariablesTextFieldWithBrowseButton())
                .widthGroup(BUILD_WIDTH_GROUP)
                .comment(ExecutionBundle.message("environment.variables.fragment.hint"))
                .bind(
                    { component -> component.envs },
                    { component, value ->  component.envs = value },
                    optionsProvider::extraEnvironmentVariables.toMutableProperty()
                )
        }
    }

    private fun Panel.createJdkComboBox(project: Project, optionsProvider: KotlinNotebookProjectOptionsProvider, disposable: Disposable): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.path")) {
            val sdkComboBox = SdkComboBox(
                SdkComboBoxModel.createProjectJdkComboBoxModel(
                    project, disposable,
                    sdkFilter = ::isSuitableForStartingKernel
                )
            )
            val sdkModel = sdkComboBox.model.sdksModel
            cell(sdkComboBox)
                .widthGroup(BUILD_WIDTH_GROUP)
                .comment(
                    KotlinNotebookBundle.message(
                        "kotlin.jupyter.settings.JDK.comment",
                        minJdkVersion.description,
                    )
                )
                .onReset {
                    val jdkName = optionsProvider.jdkName
                    if (jdkName != null) {
                        sdkComboBox.setSelectedSdk(jdkName)
                    } else {
                        sdkComboBox.selectedItem = SdkListItem.ProjectSdkItem()
                    }
                }
                .onIsModified {
                    sdkModel.isModified || optionsProvider.jdkName != sdkComboBox.selectedSdkName
                }
                .onApply {
                    if (sdkModel.isModified) {
                        sdkModel.apply()
                    }
                    optionsProvider.jdkName = sdkComboBox.selectedSdkName
                }
        }
    }

    private fun Panel.createJvmTargetForSnippetsComboBox(optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.target.for.snippets")) {
            snippetsLanguageLevelComboBox(optionsProvider::jvmTargetForSnippets)
                .widthGroup(BUILD_WIDTH_GROUP)
        }
    }

    private val SdkComboBox.selectedSdkName: String?
        get() {
            if (selectedItem is SdkListItem.ProjectSdkItem) return null
            return getSelectedSdk()?.name
        }

    private const val DEFAULT_COLUMNS_COUNT = 48
    private const val BUILD_WIDTH_GROUP = "kotlin.notebook.build"
}
