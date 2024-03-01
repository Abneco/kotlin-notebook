// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

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
import javax.swing.AbstractAction
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class SavePlotAction : AbstractExportPlotAction() {
    // For now, only one plot can be exported. See KTNB-432
    override fun isActionApplicable(outputs: Collection<LetsPlotOutputDataKey>): Boolean {
        return outputs.size == 1
    }

    override fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: BackedNotebookVirtualFile,
    ) {
        val notebookDir = notebookFile.file.parent
        val output = letsPlotOutputs.singleOrNull() ?: return
        val exportModel = showExportDialog(project, notebookDir) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            runSafely (
                {
                    val file = savePlot(output, exportModel)
                    showPlotExportedNotification(file)
                },
                { throwable ->
                    showPlotExportFailedNotification(throwable)
                }
            )
        }
    }

    private fun showExportDialog(project: Project, currentDir: VirtualFile): MutablePlotSaveModel? {
        val exportOptions = PlotExportOptions.getInstance(project)

        val model = MutablePlotSaveModel(
            exportOptions,
            currentDir.path,
        )

        val fileField = JBTextField(20)
        fileField.text = model.fileName
        fileField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                updateFileName()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                updateFileName()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                updateFileName()
            }

            private fun updateFileName() {
                model.fileName = fileField.text
            }
        })

        val formatModel = EnumComboBoxModel(ExportFormat::class.java)
        val formatComboBox = ComboBox(formatModel)

        val dialogPanel = panel {
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
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::scalingFactor.toMutableProperty(),
                            interval = PlotExportOptions.SCALING_FACTOR.range,
                            { it.toDoubleOrNull() },
                        )
                }
                row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.target.dpi")) {
                    textField()
                        .bindComparableIntervalToTextWithFixer(
                            model::targetDPI.toMutableProperty(),
                            interval = PlotExportOptions.TARGET_DPI.range,
                            { it.toIntOrNull() },
                        )
                }
            }.enabledIf(formatComboBox.selectedValueMatches { it?.isRaster == true })
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.title")) {
                enumComboBox<LetsPlotFlavor>(textListCellRenderer { it?.description })
                    .bindItem(model::letsPlotFlavor.toNullableProperty())
            }
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.directory")) {
                textFieldWithBrowseButton(
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                    .bindStringText(model::directory.toMutableProperty())
                    .align(AlignX.FILL)
            }
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.file.name")) {
                cell(fileField)
                    .bindStringText(model::fileName.toMutableProperty())
                    .align(AlignX.FILL)
            }
        }

        // There is no way for now to get rid of it
        @Suppress("DEPRECATION")
        dialogPanel.preferredWidth = 300

        val restoreDefaultSettingsAction = object : AbstractAction(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.restore.defaults")) {
            override fun actionPerformed(e: ActionEvent?) {
                exportOptions.restoreDefaults()
                dialogPanel.reset()
            }
        }

        val dialogBuilder = DialogBuilder()
            .title(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.title"))
            .apply {
                addLeftSideAction(CancelActionDescriptor().getAction(dialogWrapper))
                addAction(restoreDefaultSettingsAction)
                addOkAction().setText(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.ok.text"))
            }

        val isOk = dialogBuilder
            .centerPanel(dialogPanel)
            .showAndGet()

        return model.takeIf { isOk }
    }

    private data class MutablePlotSaveModel(
        private val options: PlotExportOptions,
        override var directory: String = System.getProperty("user.home"),
    ): PlotSaveModel {
        override var format
            get() = options.format
            set(value) {
                changeFormat(value)
            }
        override var letsPlotFlavor by options::letsPlotFlavor
        override var fileName by options::fileName
        override var scalingFactor by options::scalingFactor
        override var targetDPI by options::targetDPI

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
}
