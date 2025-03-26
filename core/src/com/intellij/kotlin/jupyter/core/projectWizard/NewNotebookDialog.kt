// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.ide.KotlinNotebookHelpId
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.description
import com.intellij.kotlin.jupyter.core.language.displayName
import com.intellij.kotlin.jupyter.core.language.provideTemplatesForCreateActions
import com.intellij.kotlin.jupyter.core.projectWizard.settings.NewNotebookMutableOptions
import com.intellij.kotlin.jupyter.core.projectWizard.settings.NewNotebookOptions
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.ui.ComponentWidthPreserver
import com.intellij.kotlin.jupyter.core.settings.ui.SegmentedButtonItem
import com.intellij.kotlin.jupyter.core.settings.ui.alignedWidthLabel
import com.intellij.kotlin.jupyter.core.settings.ui.bindLocationTextChanges
import com.intellij.kotlin.jupyter.core.settings.ui.bindSelection
import com.intellij.kotlin.jupyter.core.settings.ui.bindSelectionChanges
import com.intellij.kotlin.jupyter.core.settings.ui.bindTextChanges
import com.intellij.kotlin.jupyter.core.settings.ui.createSegmentedButton
import com.intellij.kotlin.jupyter.core.settings.ui.reactiveComment
import com.intellij.kotlin.jupyter.core.settings.ui.reactiveValidation
import com.intellij.kotlin.jupyter.core.settings.ui.withCancelActionText
import com.intellij.kotlin.jupyter.core.settings.ui.withHelpId
import com.intellij.kotlin.jupyter.core.settings.ui.withOkActionText
import com.intellij.kotlin.jupyter.core.settings.ui.withOkEnabledBy
import com.intellij.kotlin.jupyter.core.settings.ui.withPreferredWidth
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.project.ProjectStorePathManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.CHECK_DIRECTORY
import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.builder.trimmedTextValidation
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import kotlin.reflect.KMutableProperty0


class NewNotebookDialogViewModel(
    options: NewNotebookOptions,
) {
    private val generatedNameRegex = Regex("(.*)(_([1-9][0-9]*))")

    val modeSwitchValue: ObservableMutableProperty<NotebookMode> = AtomicProperty(options.notebookMode).apply {
        afterChange {
            updateAllStates()
        }
    }
    val locationFieldValue: ObservableMutableProperty<String> = AtomicProperty(options.notebookDirectory).apply {
        afterChange {
            updateAllStates()
        }
    }
    val nameFieldValue: ObservableMutableProperty<String> = AtomicProperty(options.notebookName).apply {
        afterChange {
            updateAllStatesExceptName()
        }
    }

    val locationRowIsVisible: ObservableProperty<Boolean> = modeSwitchValue.transform { it == NotebookMode.STANDARD }
    val isOkEnabled: ObservableMutableProperty<Boolean> = AtomicBooleanProperty(true)
    val notebookNameValidationMessage: ObservableMutableProperty<String?> = AtomicProperty(null)
    val notebookNameValidationInfo: ObservableProperty<ValidationInfo?> = notebookNameValidationMessage.transform { message ->
        message?.let {
            @Suppress("HardCodedStringLiteral")
            ValidationInfo(it).withOKEnabled()
        }
    }
    val locationCommentText: ObservableMutableProperty<String> = AtomicProperty("")
    val modeSwitchCommentText: ObservableMutableProperty<String> = AtomicProperty("")

    init {
      updateAllStates()
    }

    private fun updateAllStates() {
        updateNotebookName()
        updateAllStatesExceptName()
    }

    private fun updateAllStatesExceptName() {
        updateOkButtonState()
        updateLocationCommentText()
        updateModeSwitchCommentText()
    }

    private fun updateOkButtonState() {
        isOkEnabled.set(isDataValid())
    }

    private fun updateLocationCommentText() {
        val notebookDirectory = getNotebookDirectory()
        val projectExists = ProjectStorePathManager.getInstance().testStoreDirectoryExistsForProjectRoot(notebookDirectory)
        if (projectExists) {
            locationCommentText.set(KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.created.in.existent.project", notebookDirectory))
        } else {
            locationCommentText.set(KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.created.in.newly.initialized.project", notebookDirectory))
        }
    }

    private fun updateModeSwitchCommentText() {
        val text = when (modeSwitchValue.get()) {
            NotebookMode.STANDARD -> KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.type.standard.description")
            NotebookMode.LIGHT -> KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.type.light.description")
        }
        modeSwitchCommentText.set(text)
    }
    

    private fun updateNotebookName() {
        val currentName = nameFieldValue.get()
        val directory = getNotebookDirectory()

        // Match name against a pattern: whole name in group 0, base in group 1, optional counter in group 3
        val matchResult = generatedNameRegex.matchEntire(currentName)
        val baseName = matchResult?.groupValues[1] ?: currentName

        val namesSequence = sequence {
            yield(baseName)
            var counterStart = 1
            while (true) {
                yield("${baseName}_${counterStart++}")
            }
        }

        val suitableName = namesSequence
            .first { !getNotebookLocation(directory, it).toFile().exists() }

        if (currentName != suitableName) {
            nameFieldValue.set(suitableName)
        }
    }

    private fun isDataValid(): Boolean {
        return !checkFileAlreadyExists()
    }

    private fun getNotebookDirectory(): Path {
        return when (modeSwitchValue.get()) {
            NotebookMode.STANDARD -> {
                Path.of(locationFieldValue.get())
            }
            NotebookMode.LIGHT -> {
                KotlinNotebookRootTypeInstance.rootPath
            }
        }
    }

    private fun checkFileAlreadyExists(): Boolean {
        val nameWithoutExtension = nameFieldValue.get()
        val notebookPath = getNotebookLocation(getNotebookDirectory(), nameWithoutExtension)
        val alreadyExists = notebookPath.toFile().exists()
        notebookNameValidationMessage.set(
            KotlinNotebookBundle.message(
                "kotlin.notebook.new.notebook.dialog.name.already.exists",
                getNotebookName(nameWithoutExtension)
            ).takeIf { alreadyExists }
        )
        return alreadyExists
    }

    private fun getNotebookName(nameWithoutExtension: String): String {
        return "$nameWithoutExtension.${JupyterFileType.defaultExtension}"
    }

    private fun getNotebookLocation(
        directory: Path,
        nameWithoutExtension: String,
    ): Path {
        return directory.resolve(getNotebookName(nameWithoutExtension))
    }
}

fun showNewNotebookDialog(): NewNotebookOptions? {
    val options = NewNotebookMutableOptions()
    val viewModel = NewNotebookDialogViewModel(options)
    val panel = panel { buildDialogPanel(options, viewModel) }

    val isOk = DialogBuilder()
        .title(KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.title"))
        .withPreferredWidth(540)
        .withOkActionText(KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.ok.text"))
        .withOkEnabledBy(viewModel.isOkEnabled)
        .withCancelActionText(KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.cancel.text"))
        .withHelpId(KotlinNotebookHelpId.NEW_NOTEBOOK_DIALOG)
        .centerPanel(panel)
        .showAndGet()

    return options.takeIf { isOk }
}

private fun Panel.buildDialogPanel(
    options: NewNotebookMutableOptions,
    viewModel: NewNotebookDialogViewModel,
) {
    val labelWidthPreserver = ComponentWidthPreserver()

    row {
        alignedWidthLabel(labelWidthPreserver, KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.name.label"))

        textField()
            .bindText(options::notebookName)
            .columns(COLUMNS_MEDIUM)
            .align(AlignX.FILL)
            .bindTextChanges(viewModel.nameFieldValue)
            .reactiveValidation(viewModel.notebookNameValidationInfo)
    }

    row {
        alignedWidthLabel(labelWidthPreserver, KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.type.label"))

        notebookModeSegmentedButton()
            .bindSelection(options::notebookMode.toMutableProperty())
            .bindSelectionChanges(viewModel.modeSwitchValue)
            .reactiveComment(viewModel.modeSwitchCommentText)
    }

    row {
        alignedWidthLabel(labelWidthPreserver, KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.location.label"))

        textFieldWithBrowseButton(FileChooserDescriptorFactory.singleDir())
            .trimmedTextValidation(CHECK_NON_EMPTY, CHECK_DIRECTORY)
            .align(AlignX.FILL)
            .bindText(options::notebookDirectory)
            .reactiveComment(viewModel.locationCommentText)
            .bindLocationTextChanges(viewModel.locationFieldValue)
    }.visibleIf(viewModel.locationRowIsVisible)

    if (provideTemplatesForCreateActions) {
        row {
            alignedWidthLabel(labelWidthPreserver, KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.template.label"))
        }
        row {
            cell(TemplateSelectionPanel(options::template))
                .resizableColumn()
                .align(AlignX.FILL)
        }
    }
}

class TemplateSelectionPanel(val templateProperty: KMutableProperty0<NotebookTemplate>) : JBPanel<TemplateSelectionPanel>(BorderLayout()) {
    init {
        // Template list model
        val listModel = DefaultListModel<NotebookTemplate>().apply {
            addAll(NotebookTemplate.entries)
        }

        // JBList to display template options
        val templateList = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0

            installCellRenderer {
                JBLabel(it.displayName)
            }
        }

        // Description panel with JBLabel
        val descriptionLabel = JBLabel("", SwingConstants.LEFT)
        val descriptionPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(descriptionLabel, BorderLayout.NORTH)
        }

        fun updateDescriptionLabel() {
            descriptionLabel.text = templateList.selectedValue.description
            templateProperty.set(templateList.selectedValue)
        }

        // Selection listener to update description
        templateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateDescriptionLabel()
            }
        }
        templateList.selectedIndex = listModel.indexOf(templateProperty.get())
        updateDescriptionLabel()


        // Split pane layout
        val splitter = OnePixelSplitter(false, 0.3f).apply {
            firstComponent = JScrollPane(templateList)
            secondComponent = descriptionPanel

            border = BorderFactory.createLineBorder(JBColor.border(), 1, true)
            setResizeEnabled(true)
        }

        add(splitter, BorderLayout.CENTER)
    }
}

private fun Row.notebookModeSegmentedButton(): Cell<SegmentedButtonComponent<NotebookMode>> {
    fun createButtonComponent(): SegmentedButtonComponent<NotebookMode> {
        val items = listOf(
            SegmentedButtonItem(
                NotebookMode.LIGHT,
                SegmentedButton.createPresentation(text = KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.type.segmented.button.light.text"))
            ),
            SegmentedButtonItem(
                NotebookMode.STANDARD,
                SegmentedButton.createPresentation(text = KotlinNotebookBundle.message("kotlin.notebook.new.notebook.dialog.type.segmented.button.standard.text"))
            )
        )

        return createSegmentedButton(items)
    }

    return cell(createButtonComponent())
}
