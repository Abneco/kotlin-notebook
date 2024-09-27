// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.debugFeaturesEnabled
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.defaultRemoteArtifactsRepositories
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinKernelVersions.DEBUG_SUPPORTED
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookAttachedModeOptions
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.SessionOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.isAvailable
import org.jetbrains.kotlinx.jupyter.plugin.settings.isKernelVersionEnoughForInstrumentation
import org.jetbrains.kotlinx.jupyter.plugin.settings.isSuitableForStartingKernel
import org.jetbrains.kotlinx.jupyter.plugin.settings.minJdkVersion
import org.jetbrains.kotlinx.jupyter.plugin.settings.selectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.util.revealKotlinNotebookLocalKernelsFolder
import kotlin.reflect.KMutableProperty0

class KotlinNotebookSettingsPanelBuilder(
    private val project: Project,
    disposable: Disposable,
) {
    private val parentDisposable = Disposer.newCheckedDisposable(disposable)
    private val applicationOptions = KotlinNotebookApplicationOptions.get()
    private val sessionOptions = service<SessionOptionsProvider>()
    private val projectOptions = KotlinNotebookProjectOptionsProvider.getInstance(project)
    private val attachedProcessOptions = KotlinNotebookAttachedModeOptions.getInstance(project)
    private val messageBus = createMessageBus(parentDisposable)

    fun createPanel(): DialogPanel {
        return panel {
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.build")) {
                createKernelVersionSelector()

                createJdkComboBox()
                createJvmTargetForSnippetsComboBox()
                createMaxHeapSizeSpinner()
                createExtraJvmArgumentsField()
                createEnvironmentVariablesField()
            }
            if (KotlinNotebookSessionRunMode.ATTACHED_PROCESS.isAvailable) {
                group(KotlinNotebookBundle.message("kotlin.jupyter.attached.process.mode.settings.group")) {
                    createKernelHostField()
                    createZmqPortsSelector()
                }
            }
            if (debugFeaturesEnabled) {
                group(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug")) {
                    createDebugOptions()
                }
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.session")) {
                singleRowCheckBox(
                    KotlinNotebookBundle.message("checkbox.should.stop.execution.on.failure"),
                    KotlinNotebookBundle.message("checkbox.should.stop.execution.on.failure.detailed"),
                    applicationOptions::shouldStopExecutionOnFailure
                )
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.resolve.sources"), sessionOptions::resolveSources)
                singleRowCheckBox(KotlinNotebookBundle.message("checkbox.resolve.multiplatform"), sessionOptions::resolveMpp)
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs")) {
                singleRowCheckBox(
                    KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs.swing.letsPlot"),
                    applicationOptions::showLetsPlotAsSwing
                )
                singleRowCheckBox(
                    KotlinNotebookBundle.message("kotlin.jupyter.settings.outputs.swing.dataframe"),
                    applicationOptions::showDataFrameAsSwing
                )
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.typeHints")) {
                singleRowCheckBox(
                    KotlinNotebookBundle.message("checkbox.should.typehint.only.active.cell"),
                    projectOptions::shouldLimitTypeHintsByActiveCell
                )
            }
            group(KotlinNotebookBundle.message("kotlin.jupyter.settings.appearance")) {
                singleRowCheckBox(
                    KotlinNotebookBundle.message("checkbox.should.show.execution.count"),
                    applicationOptions::shouldShowExecutionCount
                )
                singleRowCheckBox(
                    KotlinNotebookBundle.message("checkbox.should.show.foldable.regions"),
                    applicationOptions::shouldShowFoldings
                )
            }
        }
    }

    private fun Panel.createKernelVersionSelector(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.version")) {
            val defaultVersion = currentKernelVersion.toMavenVersion()

            val comboBoxCell = mavenVersionComboBox(
                project,
                KotlinNotebookMavenArtifacts.KERNEL_SHADOWED,
                defaultVersion,
                projectOptions::kernelVersion,
                KotlinKernelVersion.STRING_VERSION_COMPARATOR.reversed(),
                defaultRemoteArtifactsRepositories
            ).onChanged { comboBox ->
                messageBus
                    .syncPublisher(KernelVersionSelectionChangedListener.TOPIC)
                    .onKernelVersionSelectionChanged(comboBox.selectedKernelVersion)
            }.comment(
                KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.version.description")
            )

            actionButton(
                DumbAwareAction.create(
                    KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.restore.default.version"),
                    AllIcons.General.Reset
                ) {
                    comboBoxCell.component.version = defaultVersion
                }
            ).applyToComponent {
                val button = this
                subscribeOnKernelVersionSelectionChange { newVersion ->
                    button.isEnabled = newVersion?.toMavenVersion() != defaultVersion
                }
            }

            if (ApplicationManager.getApplication().isInternal) {
                actionButton(
                    DumbAwareAction.create(
                        KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.explore.button.name"),
                        AllIcons.General.OpenDisk
                    ) {
                        project.revealKotlinNotebookLocalKernelsFolder()
                    }
                )
            }
        }
    }

    private fun Panel.createMaxHeapSizeSpinner(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.max.heap")) {
            spinner(0..99999, 100)
                .bindIntValue(projectOptions::heapMaxLimitInMib)
                .also {
                    it.validationRequestor { callback -> it.onChanged { callback() } }
                }
            label(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.max.heap.units"))
        }
    }

    private fun Panel.createExtraJvmArgumentsField(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.extra.args")) {
            expandableTextField()
                .columns(DEFAULT_COLUMNS_COUNT)
                .widthGroup(BUILD_WIDTH_GROUP)
                .applyToComponent {
                    setMonospaced(true)
                }
                .bindText(
                    { ParametersListUtil.DEFAULT_LINE_JOINER.`fun`(projectOptions.extraJvmArguments) },
                    { text -> projectOptions.extraJvmArguments = ParametersListUtil.parse(text) }
                )
        }
    }

    private fun Panel.createEnvironmentVariablesField(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.environment.variables")) {
            cell(EnvironmentVariablesTextFieldWithBrowseButton())
                .widthGroup(BUILD_WIDTH_GROUP)
                .comment(ExecutionBundle.message("environment.variables.fragment.hint"))
                .bind(
                    { component -> component.envs },
                    { component, value -> component.envs = value },
                    projectOptions::extraEnvironmentVariables.toMutableProperty()
                )
        }
    }

    private fun Panel.createJdkComboBox(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.path")) {
            val sdkComboBox = SdkComboBox(
                SdkComboBoxModel.createProjectJdkComboBoxModel(
                    project, parentDisposable,
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
                    val jdkName = projectOptions.jdkName
                    if (jdkName != null) {
                        sdkComboBox.setSelectedSdk(jdkName)
                    } else {
                        sdkComboBox.selectedItem = SdkListItem.ProjectSdkItem()
                    }
                }
                .onIsModified {
                    sdkModel.isModified || projectOptions.jdkName != sdkComboBox.selectedSdkName
                }
                .onApply {
                    if (sdkModel.isModified) {
                        sdkModel.apply()
                    }
                    projectOptions.jdkName = sdkComboBox.selectedSdkName
                }
        }
    }

    private fun Panel.createJvmTargetForSnippetsComboBox(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.target.for.snippets")) {
            snippetsLanguageLevelComboBox(projectOptions::jvmTargetForSnippets)
                .widthGroup(BUILD_WIDTH_GROUP)
        }
    }

    private fun Panel.createDebugOptions(): Row {
        return row {
            checkBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables"))
                .accessibleDescription(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.description"))
                .comment(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.port.comment", DEBUG_SUPPORTED.toMavenVersion()))
                .bindSelected(projectOptions::shouldShowNotebookVariables)
                .applyToComponent {
                    toolTipText = KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.comment")
                    subscribeOnKernelVersionSelectionChange { newVersion ->
                        isEnabled = newVersion?.isKernelVersionEnoughForInstrumentation ?: false
                    }
                }
        }
    }

    private fun Panel.createKernelHostField(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.session.attached.host")) {
            textField()
                .bindText(attachedProcessOptions::host)
                .widthGroup(BUILD_WIDTH_GROUP)
        }
    }

    private fun Panel.createZmqPortsSelector(): Row {
        return group(KotlinNotebookBundle.message("kotlin.jupyter.settings.session.attached.ports")) {
            for (socketType in JupyterSocketType.entries) {
                @Suppress("HardCodedStringLiteral")
                val socketName = socketType.name
                row(socketName) {
                    textField()
                        .bindIntText(attachedProcessOptions.getSocketProperty(socketType))
                        .widthGroup(BUILD_WIDTH_GROUP)
                }
            }
        }
    }

    private fun Panel.singleRowCheckBox(@NlsContexts.Checkbox checkBoxTitle: String, property: KMutableProperty0<Boolean>) {
        singleRowCheckBox(checkBoxTitle, null, property)
    }

    private fun Panel.singleRowCheckBox(
        @NlsContexts.Checkbox checkBoxTitle: String,
        @NlsContexts.DetailedDescription detailedDescription: String?,
        property: KMutableProperty0<Boolean>
    ) {
        row(null) {
            checkBox(checkBoxTitle)
                .apply {
                    val commentMessage: @NlsContexts.DetailedDescription String = detailedDescription ?: return@apply
                    comment(commentMessage)
                }
                .bindSelected(property)
        }
    }

    private val SdkComboBox.selectedSdkName: String?
        get() {
            if (selectedItem is SdkListItem.ProjectSdkItem) return null
            return getSelectedSdk()?.name
        }

    private fun createMessageBus(parentDisposable: CheckedDisposable): MessageBus {
        return MessageBusFactory.newMessageBus(object : MessageBusOwner {
            override fun createListener(descriptor: ListenerDescriptor): Any {
                throw UnsupportedOperationException()
            }

            override fun isDisposed() = parentDisposable.isDisposed
        })
    }

    private fun subscribeOnKernelVersionSelectionChange(listener: KernelVersionSelectionChangedListener) {
        messageBus.connect(parentDisposable).subscribe(KernelVersionSelectionChangedListener.TOPIC, listener)
    }

    private fun interface KernelVersionSelectionChangedListener {
        fun onKernelVersionSelectionChanged(newVersion: KotlinKernelVersion?)

        companion object {
            @Topic.ProjectLevel
            val TOPIC = Topic(KernelVersionSelectionChangedListener::class.java, Topic.BroadcastDirection.NONE)
        }
    }

    companion object {
        private const val DEFAULT_COLUMNS_COUNT = 48
        private const val BUILD_WIDTH_GROUP = "kotlin.notebook.build"
    }
}
