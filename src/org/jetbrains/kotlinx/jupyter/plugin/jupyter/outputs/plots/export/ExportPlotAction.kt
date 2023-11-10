// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.util.preferredWidth
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.MutableLetsPlotSpec
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.PlotDataKeyExtractor
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.letsPlot.awt.plot.PlotSvgExport
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport.buildImageFromRawSpecs
import org.jetbrains.letsPlot.core.util.PlotHtmlExport
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import org.jetbrains.plugins.notebooks.core.api.getNotebookCellAndFile
import org.jetbrains.plugins.notebooks.core.impl.actions.NotebookEditorActionBase
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.actions.getNotebookFile
import org.jetbrains.plugins.notebooks.jupyter.editor.getCellIndex
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookObjectOutputDataKeyExtractor
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterDisplayDataOutput
import java.io.File
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class ExportPlotAction : NotebookEditorActionBase() {
    override fun actionPerformed(event: AnActionEvent) {
        val letsPlotOutputs = getLetsPlotOutputs(event)

        val plotData = letsPlotOutputs.singleOrNull()?.data ?: return
        val extractor = NotebookObjectOutputDataKeyExtractor.EP_NAME.findExtension(PlotDataKeyExtractor::class.java) ?: return
        val spec = extractor.extractKey(plotData, null)?.spec ?: return

        val notebookFile = event.getNotebookFile() ?: return
        val notebookDir = notebookFile.file.parent

        val exportModel = showExportDialog(notebookDir) ?: return

        export(deserializeSpec(spec).toMutableMap(), exportModel)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        super.update(event)
        val letsPlotOutputs = getLetsPlotOutputs(event)
        event.presentation.isEnabledAndVisible = letsPlotOutputs.size == 1
    }

    private fun showExportDialog(currentDir: VirtualFile): ExportModel? {
        val model = ExportModel()
        model.directory = currentDir.path

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

        val formatComboBox = ComboBox(ExportFormat.entries.toTypedArray())
        formatComboBox.selectedItem = model.format
        formatComboBox.addActionListener {
            model.changeFormat(formatComboBox.selectedItem as ExportFormat)
            fileField.setText(model.fileName)
        }

        val dialogBuilder = DialogBuilder()
            .title(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.title"))
            .apply {
                addCancelAction()
                addOkAction().setText(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.ok.text"))
            }

        val dialogPanel = panel {
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.format")) {
                cell(formatComboBox)
            }
            indent {
                row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.scaling.factor")) {
                    textField()
                        .bindText(
                            MutableProperty(
                                { model.scalingFactor.toString() },
                                { value -> model.scalingFactor = value.toDoubleOrNull() ?: return@MutableProperty }
                            )
                        )
                }
                row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.target.dpi")) {
                    textField()
                        .bindIntText(model::targetDPI)
                }
            }.enabledIf(formatComboBox.selectedValueMatches { it?.isRaster == true })
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.directory")) {
                textFieldWithBrowseButton(
                    fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                )
                    .bindText(model::directory.toMutableProperty())
                    .align(AlignX.FILL)
            }
            row(KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.file.name")) {
                cell(fileField)
                    .bindText(model::fileName.toMutableProperty())
                    .align(AlignX.FILL)
            }
        }

        dialogPanel.preferredWidth = 300

        val isOk = dialogBuilder
            .centerPanel(dialogPanel)
            .showAndGet()

        return model.takeIf { isOk }
    }

    private fun export(spec: MutableLetsPlotSpec, model: ExportModel) {
        val file = File(model.directory, model.fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        when (val modelFormat = model.format) {
            ExportFormat.SVG -> {
                val svg = PlotSvgExport.buildSvgImageFromRawSpecs(spec)
                file.writeText(svg)
            }
            ExportFormat.HTML -> {
                val html = PlotHtmlExport.buildHtmlFromRawSpecs(spec, iFrame = true, scriptUrl = PlotHtmlHelper.scriptUrl("4.0.0"))
                file.writeText(html)
            }
            else  -> {
                val format = when (modelFormat) {
                    ExportFormat.PNG -> PlotImageExport.Format.PNG
                    ExportFormat.JPG -> PlotImageExport.Format.JPEG()
                    else -> throw IllegalStateException("No other formats are possible on this stage")
                }
                // todo take parameters from model
                val byteArray = buildImageFromRawSpecs(
                    plotSpec = spec,
                    format = format,
                    scalingFactor = model.scalingFactor,
                    targetDPI = model.targetDPI.toDouble(),
                ).bytes
                file.writeBytes(byteArray)
            }

        }
    }

    private fun getLetsPlotOutputs(event: AnActionEvent): List<JupyterDisplayDataOutput> {
        val (psiCell, notebookVirtualFile) = event.dataContext.getNotebookCellAndFile() ?: return emptyList()
        return getLetsPlotOutputs(notebookVirtualFile, psiCell.getCellIndex())
    }

    private fun getLetsPlotOutputs(notebookVirtualFile: BackedNotebookVirtualFile, cellIndex: Int): List<JupyterDisplayDataOutput> {
        val notebook = notebookVirtualFile.notebook
        val jupyterCell = notebook.cells[cellIndex]

        val outputs = jupyterCell.outputs ?: return emptyList()

        return outputs.outputs.filterIsInstanceAnd { output ->
            output.data.has(PlotDataKeyExtractor.PLOT_KEY)
        }
    }

    private enum class ExportFormat(val isRaster: Boolean = false) {
        SVG,
        PNG(true),
        JPG(true),
        HTML,
    }

    private data class ExportModel(
        var format: ExportFormat = ExportFormat.SVG,
        var directory: String = System.getProperty("user.home"),
        var fileName: String = "plot.svg",
        var scalingFactor: Double = 2.0,
        var targetDPI: Int = 4000,
    ) {
        fun changeFormat(newFormat: ExportFormat) {
            format = newFormat
            val extension = format.toString().lowercase()
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
