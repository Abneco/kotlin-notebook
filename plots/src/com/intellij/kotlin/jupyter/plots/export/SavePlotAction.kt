// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.core.settings.ui.bindComparableIntervalToTextWithFixer
import com.intellij.kotlin.jupyter.core.settings.ui.bindStringText
import com.intellij.kotlin.jupyter.core.settings.ui.enumComboBox
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.runSafely
import com.intellij.kotlin.jupyter.plots.LetsPlotFlavor
import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey
import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogBuilder.CancelActionDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.util.preferredWidth
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.AbstractAction


abstract class SavePlotAction : AbstractExportPlotAction() {
    override fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: VirtualFile,
    ) {
        val notebookDir = notebookFile.parent
        val exportModel = showExportDialog(project, notebookDir, letsPlotOutputs.size > 1) ?: return

        KotlinNotebookPluginScope.getForProject(project).async {
            savePlots(letsPlotOutputs, exportModel)
        }
    }

    @RequiresBackgroundThread
    private suspend fun savePlots(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        exportModel: MutablePlotSaveModel
    ) {
        val savedFiles = mutableListOf<File>()
        val skippedFiles = mutableListOf<File>()
        val errors = mutableListOf<Throwable>()

        for ((outputIndex, output) in letsPlotOutputs.withIndex()) {
            val isLastFile = outputIndex == letsPlotOutputs.lastIndex
            runSafely(
                {
                    val fileSaveRequest = exportModel.createFileSaveRequest(outputIndex + 1, isLastFile)
                    val file = fileSaveRequest.file
                    if (fileSaveRequest.shouldSave) {
                        file.parentFile.mkdirs()
                        savePlot(output, exportModel, file)
                        savedFiles.add(file)
                    } else {
                        skippedFiles.add(file)
                    }
                },
                { throwable ->
                    errors.add(throwable)
                }
            )
        }

        showPlotSaveNotification(savedFiles, skippedFiles, errors)
    }

    private fun showExportDialog(
        project: Project,
        currentDir: VirtualFile,
        multipleOutputs: Boolean
    ): MutablePlotSaveModel? {
        val exportOptions = PlotExportOptions.getInstance(project)

        val model = MutablePlotSaveModel(
            exportOptions,
            currentDir.path,
        )

        val dialogBuilder = DialogBuilder()

        fun panelMessage(key: String) = KotlinNotebookPlotsBundle.message(
            if (multipleOutputs) "$key.multiple" else key
        )

        fun verifyModel(): Boolean {
            return !(multipleOutputs && OUTPUT_INDEX_TEMPLATE !in model.fileName)
        }

        fun updateOkAction() {
            dialogBuilder.okActionEnabled(verifyModel())
        }

        updateOkAction()

        val dialogPanel = panel {
            val fileField = JBTextField(20)

            val formatModel = EnumComboBoxModel(ExportFormat::class.java)
            val formatComboBox = ComboBox(formatModel)

            row(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.format")) {
                cell(formatComboBox)
                    .bindItem(model::format.toNullableProperty())
                    .applyToComponent {
                        addActionListener {
                            model.format = formatModel.selectedItem
                            fileField.setText(model.fileName)
                        }
                    }
            }
            indent {
                row(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.scaling.factor")) {
                    val scalingFactorOption = PlotExportOptions.SCALING_FACTOR
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::scalingFactor.toMutableProperty(),
                            interval = scalingFactorOption.range,
                            { it.toDoubleOrNull() },
                        )
                        .comment(KotlinNotebookPlotsBundle.message(
                                "kotlin.jupyter.dialog.outputs.plot.export.scaling.factor.comment",
                                scalingFactorOption.min,
                                scalingFactorOption.max
                        ))
                }
                row(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.target.dpi")) {
                    val targetDpiOption = PlotExportOptions.TARGET_DPI
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::targetDPI.toMutableProperty(),
                            interval = targetDpiOption.range,
                            { it.toIntOrNull() },
                        )
                        .comment(KotlinNotebookPlotsBundle.message(
                                "kotlin.jupyter.dialog.outputs.plot.export.target.dpi.comment",
                                targetDpiOption.min,
                                targetDpiOption.max
                        ))
                }
            }.enabledIf(formatComboBox.selectedValueMatches { it?.isRaster == true })
            row(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.title")) {
                enumComboBox<LetsPlotFlavor>(textListCellRenderer { it?.description })
                    .bindItem(model::letsPlotFlavor.toNullableProperty())
            }
            row(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.directory")) {
                textFieldWithBrowseButton(
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                    .bindStringText(model::directory.toMutableProperty())
                    .align(AlignX.FILL)
                    .comment(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.directory.comment"))
            }
            row(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.file.name")) {
                cell(fileField)
                    .bindStringText(model::fileName.toMutableProperty())
                    .align(AlignX.FILL)
                    .onChanged {
                        model.fileName = fileField.text
                        updateOkAction()
                    }
                    .comment(KotlinNotebookPlotsBundle.message(
                        "kotlin.jupyter.dialog.outputs.plot.export.file.name.comment",
                        OUTPUT_INDEX_TEMPLATE
                    ))
            }
        }

        // There is no way for now to get rid of it
        @Suppress("DEPRECATION")
        dialogPanel.preferredWidth = 550

        val restoreDefaultSettingsAction = object : AbstractAction(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.restore.defaults")) {
            override fun actionPerformed(e: ActionEvent?) {
                exportOptions.restoreDefaults()
                dialogPanel.reset()
            }
        }

        val isOk = dialogBuilder
            .title(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.title"))
            .apply {
                addLeftSideAction(CancelActionDescriptor().getAction(dialogWrapper))
                addAction(restoreDefaultSettingsAction)
                addOkAction().setText(KotlinNotebookPlotsBundle.message("kotlin.jupyter.dialog.outputs.plot.export.ok.text"))
            }
            .centerPanel(dialogPanel)
            .showAndGet()

        return model.takeIf { isOk }
    }

    private data class MutablePlotSaveModel(
      private val options: PlotExportOptions,
      var directory: String = System.getProperty("user.home"),
    ): PlotExportModel {
        override var format
            get() = options.format
            set(value) {
                changeFormat(value)
            }
        override var letsPlotFlavor by options::letsPlotFlavor
        var fileName by options::fileName
        override var scalingFactor by options::scalingFactor
        override var targetDPI by options::targetDPI

        private var fileAlreadyExistsStrategy = FileAlreadyExistsStrategy.ASK
        private var rememberStrategyChoice: Boolean = false

        suspend fun createFileSaveRequest(outputIndex: Int, isLastFile: Boolean): FileSaveRequest {
            val myDirectory = directory
            val fileNameTemplate = fileName.replace(OUTPUT_INDEX_TEMPLATE, outputIndex.toString())
            val file = File(myDirectory, fileNameTemplate)
            val newFile = ensureFileDoesNotExist(file, !isLastFile)
            return FileSaveRequest(newFile ?: file, newFile != null)
        }

        /**
         * Depending on file existence and the strategy chosen by the user,
         * returns the file where the plot should be saved or null if it shouldn't be saved
         */
        suspend fun ensureFileDoesNotExist(file: File, showRememberChoiceCheckbox: Boolean): File? {
            if (!file.exists()) return file

            if (fileAlreadyExistsStrategy == FileAlreadyExistsStrategy.ASK) {
                withContext(Dispatchers.EDT) {
                    showFileAlreadyExistsDialog(
                        file = file,
                        showRememberChoiceCheckbox = showRememberChoiceCheckbox,
                        rememberChoiceProperty = ::rememberStrategyChoice.toMutableProperty(),
                        strategyProperty = ::fileAlreadyExistsStrategy.toMutableProperty(),
                    )
                }
            }

            val currentStrategy = fileAlreadyExistsStrategy

            if (!rememberStrategyChoice) {
                fileAlreadyExistsStrategy = FileAlreadyExistsStrategy.ASK
            }

            return when (currentStrategy) {
                FileAlreadyExistsStrategy.ASK,
                FileAlreadyExistsStrategy.SKIP -> null
                FileAlreadyExistsStrategy.OVERWRITE -> file
                FileAlreadyExistsStrategy.CREATE_NEW -> {
                    generateSequence(1) { it + 1 }
                        .map { File(file.parentFile, "${file.nameWithoutExtension} ($it).${file.extension}")  }
                        .first { !it.exists() }
                }
            }
        }

        private fun changeFormat(newFormat: ExportFormat) {
            options.format = newFormat
            val extension = newFormat.extension
            if (fileName.isBlank()) {
                fileName = "plot.$extension"
            }
            val fileNameParts = fileName.split('.')
            if (fileNameParts.size == 1) {
                fileName += ".$extension"
            } else {
                fileName = fileNameParts.dropLast(1).joinToString(".") + ".$extension"
            }
        }
    }

    private class FileSaveRequest(
        val file: File,
        val shouldSave: Boolean,
    )

    companion object {
        private const val OUTPUT_INDEX_TEMPLATE = "%idx%"
    }
}
