// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.ButtonsGroup
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.SessionOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.isKernelProcessEmbeddingEnabled
import org.jetbrains.kotlinx.jupyter.plugin.settings.isSuitableForStartingKernel
import org.jetbrains.kotlinx.jupyter.plugin.settings.minJdkVersion
import org.jetbrains.kotlinx.jupyter.plugin.util.revealKotlinNotebookLocalKernelsFolder
import kotlin.reflect.KMutableProperty0

object KotlinNotebookSettingsPanel {
    fun createPanel(
        project: Project,
        parentDisposable: Disposable,
    ): DialogPanel {
        val applicationOptions = KotlinNotebookApplicationOptions.get()
        val sessionOptions = service<SessionOptionsProvider>()
        val projectOptions = KotlinNotebookProjectOptionsProvider.getInstance(project)

        return panel {
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.build")) {
                createKernelVersionSelector(project, projectOptions)

                val kernelModeObservable = getKernelRunModeObservable(projectOptions)

                if (isKernelProcessEmbeddingEnabled) {
                    createKernelModeSelector(projectOptions, kernelModeObservable)
                }

                val showSeparateProcessSettings = kernelModeObservable.transform { it == KotlinNotebookSessionRunMode.SEPARATE_PROCESS }
                fun Row.showForSeparateProcess(): Row = visibleIf(showSeparateProcessSettings)

                createJdkComboBox(project, projectOptions, parentDisposable).showForSeparateProcess()
                createJvmTargetForSnippetsComboBox(projectOptions)
                createMaxHeapSizeSpinner(projectOptions).showForSeparateProcess()
                createExtraJvmArgumentsField(projectOptions).showForSeparateProcess()
                createEnvironmentVariablesField(projectOptions).showForSeparateProcess()
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug")) {
                createDebugOptions(projectOptions)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.session")) {
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.resolve.sources"), sessionOptions::resolveSources)
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.resolve.multiplatform"), sessionOptions::resolveMpp)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs")) {
                singleRowCheckBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs.swing.letsPlot"), applicationOptions::showLetsPlotAsSwing)
                singleRowCheckBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs.swing.dataframe"), applicationOptions::showDataFrameAsSwing)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.typeHints")) {
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.should.typehint.only.active.cell"), projectOptions::shouldLimitTypeHintsByActiveCell)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.appearance")) {
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.should.show.execution.count"), applicationOptions::shouldShowExecutionCount)
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.should.show.foldable.regions"), applicationOptions::shouldShowFoldings)
            }
        }
    }

    private fun getKernelRunModeObservable(
        optionsProvider: KotlinNotebookProjectOptionsProvider
    ): ObservableMutableProperty<KotlinNotebookSessionRunMode> {
        val modeProperty = optionsProvider::kernelRunMode
        return AtomicProperty(modeProperty.invoke())
    }

    private fun Panel.createKernelVersionSelector(project: Project, optionsProvider: KotlinNotebookProjectOptionsProvider): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.version")) {
            mavenVersionComboBox(
                project,
                KotlinNotebookMavenArtifacts.KERNEL_SHADOWED,
                optionsProvider::kernelVersion,
                KotlinKernelVersion.STRING_VERSION_COMPARATOR.reversed(),
            )
            button(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.explore.button.name")) {
                project.revealKotlinNotebookLocalKernelsFolder()
            }.visibleIf(ComponentPredicate.fromValue(ApplicationManager.getApplication().isInternal))
        }
    }

    private fun Panel.createKernelModeSelector(
        optionsProvider: KotlinNotebookProjectOptionsProvider,
        kernelModeObservable: ObservableMutableProperty<KotlinNotebookSessionRunMode>
    ): ButtonsGroup {
        return buttonsGroup(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode")) {
            for (value in KotlinNotebookSessionRunMode.entries) {
                row {
                    radioButton(value.description, value).onChanged { button ->
                        if (button.isSelected) {
                            kernelModeObservable.set(value)
                        }
                    }
                }
            }
        }.bind(optionsProvider::kernelRunMode)
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

    private fun Panel.createDebugOptions(optionsProvider: KotlinNotebookProjectOptionsProvider) {
        var checkBox: Cell<JBCheckBox>? = null
        row {
            checkBox = checkBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.port.check.box"))
                .accessibleDescription(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.port.check.box.description"))
                .bindSelected(optionsProvider::shouldOpenDebugPort)
        }

        row {
            checkBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables"))
                .accessibleDescription(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.description"))
                .bindSelected(optionsProvider::shouldShowNotebookVariables)
                .enabledIf(checkBox?.selected ?: ComponentPredicate.FALSE).applyToComponent {
                    toolTipText = KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.comment")
                    checkBox?.selected?.addListener { checkBoxValue ->
                        if (!checkBoxValue) isSelected = false
                    }
                }
        }
    }

    private fun Panel.singleRowCheckBox(@NlsContexts.Checkbox checkBoxTitle: String, property: KMutableProperty0<Boolean>) {
        row(null) {
            checkBox(checkBoxTitle).bindSelected(property)
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
