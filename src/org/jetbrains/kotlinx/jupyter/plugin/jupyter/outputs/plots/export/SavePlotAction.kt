// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.util.preferredWidth
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotFlavor
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.bindComparableIntervalToTextWithFixer
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.bindStringText
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.enumComboBox
import org.jetbrains.kotlinx.jupyter.plugin.util.runSafely
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.awt.event.ActionEvent
import java.io.File
import javax.swing.AbstractAction


class SavePlotAction : AbstractExportPlotAction() {
    override fun doUpdate(event: AnActionEvent, letsPlotOutputs: List<LetsPlotOutputDataKey>) {
        super.doUpdate(event, letsPlotOutputs)
        val multipleOutputs = letsPlotOutputs.size > 1
        if (multipleOutputs) {
            event.presentation.text = KotlinNotebookBundle.message("action.ExportLetsPlot.text.multiple")
            event.presentation.description = KotlinNotebookBundle.message("action.ExportLetsPlot.description.multiple")
        }
    }

    override fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: BackedNotebookVirtualFile,
    ) {
        val notebookDir = notebookFile.file.parent
        val exportModel = showExportDialog(project, notebookDir, letsPlotOutputs.size > 1) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val files = mutableListOf<File>()
            val errors = mutableListOf<Throwable>()

            for ((outputIndex, output) in letsPlotOutputs.withIndex()) {
                runSafely (
                    {
                        val file = exportModel.prepareFile(outputIndex)
                        savePlot(output, exportModel, file)
                        files.add(file)
                    },
                    { throwable ->
                        errors.add(throwable)
                    }
                )
            }

            showPlotSaveNotification(files, errors)
        }
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

        fun panelMessage(key: String) = KotlinNotebookBundle.message(
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

            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.format")) {
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
                row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.scaling.factor")) {
                    val scalingFactorOption = PlotExportOptions.SCALING_FACTOR
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::scalingFactor.toMutableProperty(),
                            interval = scalingFactorOption.range,
                            { it.toDoubleOrNull() },
                        )
                        .comment(KotlinNotebookBundle.message(
                                "kotlin.jupyter.dialog.outputs.plot.export.scaling.factor.comment",
                                scalingFactorOption.min,
                                scalingFactorOption.max
                        ))
                }
                row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.target.dpi")) {
                    val targetDpiOption = PlotExportOptions.TARGET_DPI
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::targetDPI.toMutableProperty(),
                            interval = targetDpiOption.range,
                            { it.toIntOrNull() },
                        )
                        .comment(KotlinNotebookBundle.message(
                                "kotlin.jupyter.dialog.outputs.plot.export.target.dpi.comment",
                                targetDpiOption.min,
                                targetDpiOption.max
                        ))
                }
            }.enabledIf(formatComboBox.selectedValueMatches { it?.isRaster == true })
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.title")) {
                enumComboBox<LetsPlotFlavor>(textListCellRenderer { it?.description })
                    .bindItem(model::letsPlotFlavor.toNullableProperty())
            }
            row(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.directory")) {
                textFieldWithBrowseButton(
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                    .bindStringText(model::directory.toMutableProperty())
                    .align(AlignX.FILL)
                    .comment(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.directory.comment"))
            }
            row(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.file.name")) {
                cell(fileField)
                    .bindStringText(model::fileName.toMutableProperty())
                    .align(AlignX.FILL)
                    .onChanged {
                        model.fileName = fileField.text
                        updateOkAction()
                    }
                    .comment(KotlinNotebookBundle.message(
                        "kotlin.jupyter.dialog.outputs.plot.export.file.name.comment",
                        OUTPUT_INDEX_TEMPLATE
                    ))
            }
            row(panelMessage("kotlin.jupyter.dialog.outputs.plot.export.overwrite.existing")) {
                checkBox("")
                    .bindSelected(model::overwriteExistingFiles.toMutableProperty())
            }
        }

        // There is no way for now to get rid of it
        @Suppress("DEPRECATION")
        dialogPanel.preferredWidth = 550

        val restoreDefaultSettingsAction = object : AbstractAction(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.restore.defaults")) {
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
                addOkAction().setText(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.ok.text"))
            }
            .centerPanel(dialogPanel)
            .showAndGet()

        return model.takeIf { isOk }
    }

    private fun MutablePlotSaveModel.prepareFile(outputIndex: Int): File {
        val file = getFile(outputIndex + 1)
        if (!overwriteExistingFiles && file.exists()) {
            throw FileAlreadyExistsException(file)
        }
        file.parentFile.mkdirs()
        return file
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
        var overwriteExistingFiles by options::overwriteExistingFiles
        override var scalingFactor by options::scalingFactor
        override var targetDPI by options::targetDPI

        fun getFile(outputIndex: Int): File {
            val myDirectory = directory
            val fileNameTemplate = fileName.replace(OUTPUT_INDEX_TEMPLATE, outputIndex.toString())
            return File(myDirectory, fileNameTemplate)
        }

        private fun changeFormat(newFormat: ExportFormat) {
            options.format = newFormat
            val extension = newFormat.toString().lowercase()
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

    companion object {
        private const val OUTPUT_INDEX_TEMPLATE = "%idx%"
    }
}
