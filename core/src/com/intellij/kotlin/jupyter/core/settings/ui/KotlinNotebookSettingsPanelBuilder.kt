// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.kotlin.jupyter.core.debug.util.debugFeaturesEnabled
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.defaultRemoteArtifactsRepositories
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinKernelVersions.DEBUG_SUPPORTED
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookAttachedModeOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.SessionOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.extensions.NotebookCompilerPluginSettingsBuilder.Companion.buildCompilerPluginsOptionsSelector
import com.intellij.kotlin.jupyter.core.settings.isAvailable
import com.intellij.kotlin.jupyter.core.settings.isKernelVersionEnoughForInstrumentation
import com.intellij.kotlin.jupyter.core.settings.isSuitableForStartingKernel
import com.intellij.kotlin.jupyter.core.settings.minJdkVersion
import com.intellij.kotlin.jupyter.core.settings.projectWideExtraCompilerArgumentsSelectionEnabled
import com.intellij.kotlin.jupyter.core.settings.replCompilerModeSelectorEnabled
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersion
import com.intellij.kotlin.jupyter.core.util.revealKotlinNotebookLocalKernelsFolder
import com.intellij.openapi.Disposable
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
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.ButtonsGroup
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.PluginListenerDescriptor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
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
                createReplCompilerModeSelector()
                createJdkComboBox()
                createJvmTargetForSnippetsComboBox()
                createMaxHeapSizeSpinner()
                createExtraJvmArgumentsField()
                createCompilerExtraArgumentsField()
                createEnvironmentVariablesField()
                createCompilerPluginsOptionsSelector()
            }
            if (KotlinNotebookSessionRunMode.ATTACHED_PROCESS.isAvailable) {
                group(KotlinNotebookBundle.message("kotlin.jupyter.attached.process.mode.settings.group")) {
                    createAttachedProcessKernelHostField()
                    createAttachedProcessPortSelector()
                }
            }
            if (debugFeaturesEnabled) {
                group(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug")) {
                    createVariablesViewSelector()
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

    private fun Panel.createReplCompilerModeSelector(): ButtonsGroup? {
        if (!replCompilerModeSelectorEnabled) return null
        return buttonsGroup {
            row("") {
                radioButton(KotlinNotebookBundle.message("checkbox.replCompilerMode.k1Mode"), ReplCompilerMode.K1)
                radioButton(KotlinNotebookBundle.message("checkbox.replCompilerMode.k2Mode"), ReplCompilerMode.K2)
            }
        }.bind<ReplCompilerMode>(applicationOptions::replCompilerMode)
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
        return createParametersListField(
            KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.extra.args"),
            KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.extra.args.comment"),
            projectOptions::extraJvmArguments,
        )
    }

    private fun Panel.createCompilerExtraArgumentsField(): Row? {
        if (!projectWideExtraCompilerArgumentsSelectionEnabled) return null

        return createParametersListField(
            KotlinNotebookBundle.message("kotlin.jupyter.settings.compiler.extra.args"),
            null,
            projectOptions::extraCompilerArguments,
        )
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

    private fun Panel.createCompilerPluginsOptionsSelector(): Row? {
        return buildCompilerPluginsOptionsSelector(project, this)
    }

    private fun Panel.createVariablesViewSelector() {
        var variablesBox: Cell<JBCheckBox>? = null
        row {
            variablesBox = checkBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables"))
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
        row {
            checkBox(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.focus.check.box"))
                .bindSelected(projectOptions::shouldFocusOnVariables)
                .accessibleDescription(KotlinNotebookBundle.message("kotlin.jupyter.settings.jvm.debug.variables.focus.check.box.description"))
                .applyToComponent {
                    isEnabled = projectOptions.shouldShowNotebookVariables
                    variablesBox?.onChanged {
                        isEnabled = it.isEnabled && it.isSelected
                    }
                }
        }
    }

    private fun Panel.createAttachedProcessKernelHostField(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.session.attached.host")) {
            textField()
                .bindText(attachedProcessOptions::host)
                .widthGroup(BUILD_WIDTH_GROUP)
        }
    }

    private fun Panel.createAttachedProcessPortSelector(): Row {
        return row(KotlinNotebookBundle.message("kotlin.jupyter.settings.session.attached.ports")) {
            textField()
                .bindIntText(attachedProcessOptions::webSocketPort)
                .widthGroup(BUILD_WIDTH_GROUP)
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

    private fun Panel.createParametersListField(
        message: @Nls String,
        commentMessage: @NlsContexts.DetailedDescription String?,
        property: KMutableProperty0<MutableList<String>>,
    ): Row {
        return row(message) {
            expandableTextField()
                .apply {
                    if (commentMessage != null) {
                        comment(commentMessage)
                    }
                }
                .columns(DEFAULT_COLUMNS_COUNT)
                .widthGroup(BUILD_WIDTH_GROUP)
                .applyToComponent {
                    setMonospaced(true)
                }
                .bindText(
                    { ParametersListUtil.DEFAULT_LINE_JOINER.`fun`(property.get()) },
                    { text -> property.set(ParametersListUtil.parse(text)) }
                )
        }
    }

    private val SdkComboBox.selectedSdkName: String?
        get() {
            if (selectedItem is SdkListItem.ProjectSdkItem) return null
            return getSelectedSdk()?.name
        }

    private fun createMessageBus(parentDisposable: CheckedDisposable): MessageBus {
        return MessageBusFactory.newMessageBus(object : MessageBusOwner {
            override fun createListener(descriptor: PluginListenerDescriptor): Any {
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
        const val BUILD_WIDTH_GROUP: String = "kotlin.notebook.build"
    }
}
