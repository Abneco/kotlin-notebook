// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.ide.actions.OpenFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.ui.util.preferredWidth
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotComponent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.MutableLetsPlotSpec
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.PlotDataKeyExtractor
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.addTextFocusLostFixer
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.bindDoubleText
import org.jetbrains.kotlinx.jupyter.plugin.util.firstAncestorOfType
import org.jetbrains.kotlinx.jupyter.plugin.util.runSafely
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

        val spec = letsPlotOutputs.singleOrNull()?.spec ?: return
        val notebookFile = event.getNotebookFile() ?: return
        val notebookDir = notebookFile.file.parent

        val exportModel = showExportDialog(notebookDir) ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            runSafely (
                {
                    val file = export(deserializeSpec(spec).toMutableMap(), exportModel)
                    showPlotExportedNotification(file)
                },
                { throwable ->
                    showPlotExportFailedNotification(throwable)
                }
            )
        }
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
                    /**
                     * These values are taken from [buildImageFromRawSpecs].
                     * For the upper bound see https://github.com/JetBrains/lets-plot/issues/1011
                     */
                    val minScalingFactor = 0.1
                    val maxScalingFactor = 9.0
                    val defaultScalingFactor = model.scalingFactor

                    val scalingFactorValidator: (Double) -> Boolean = { it in minScalingFactor..maxScalingFactor }
                    require(scalingFactorValidator(defaultScalingFactor))

                    textField()
                        .bindDoubleText(model::scalingFactor.toMutableProperty(), scalingFactorValidator)
                        .addTextFocusLostFixer { oldText ->
                            val value = oldText.toDoubleOrNull()
                            val newValue = when {
                                value == null -> defaultScalingFactor
                                value < minScalingFactor -> minScalingFactor
                                value > maxScalingFactor -> maxScalingFactor
                                else-> null
                            }
                            newValue?.toString()
                        }
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

    @RequiresBackgroundThread
    private fun export(spec: MutableLetsPlotSpec, model: ExportModel): File {
        val file = File(model.directory, model.fileName)
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
                val byteArray = buildImageFromRawSpecs(
                    plotSpec = spec,
                    format = format,
                    scalingFactor = model.scalingFactor,
                    targetDPI = model.targetDPI.toDouble(),
                ).bytes
                file.writeBytes(byteArray)
            }
        }

        return file
    }

    private fun getLetsPlotOutputs(event: AnActionEvent): List<LetsPlotOutputDataKey> {
        val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val letsPlotComponent = contextComponent?.firstAncestorOfType<LetsPlotComponent>()
        if (letsPlotComponent != null) {
            return listOfNotNull(letsPlotComponent.dataKey)
        }

        val (psiCell, notebookVirtualFile) = event.dataContext.getNotebookCellAndFile() ?: return emptyList()
        return getLetsPlotOutputs(notebookVirtualFile, psiCell.getCellIndex())
    }

    private fun getLetsPlotOutputs(notebookVirtualFile: BackedNotebookVirtualFile, cellIndex: Int): List<LetsPlotOutputDataKey> {
        val notebook = notebookVirtualFile.notebook
        val jupyterCell = notebook.cells[cellIndex]

        val outputs = jupyterCell.outputs ?: return emptyList()

        val extractor = NotebookObjectOutputDataKeyExtractor.EP_NAME.findExtension(PlotDataKeyExtractor::class.java) ?: return emptyList()

        return outputs.outputs.filterIsInstanceAnd<JupyterDisplayDataOutput> { output ->
            output.data.has(PlotDataKeyExtractor.PLOT_KEY)
        }.mapNotNull { letPlotOutput ->
            extractor.extractKey(letPlotOutput.data.toV4Json(), null)
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

private const val KANDY_NOTIFICATIONS_GROUP = "Kandy plot export"

private fun showPlotExportFailedNotification(throwable: Throwable) {
    @NlsSafe
    val exceptionText = throwable.message.orEmpty()

    val notification = Notification(
        KANDY_NOTIFICATIONS_GROUP,
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.failed.notification.message"),
        exceptionText,
        NotificationType.ERROR
    )

    Notifications.Bus.notify(notification)
}

private fun showPlotExportedNotification(file: File) {
    val notification = Notification(
        KANDY_NOTIFICATIONS_GROUP,
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.message", file.name),
        NotificationType.INFORMATION
    )

    notification.addAction(
        NotificationAction.create(KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open")) { e ->
            val project = e.project ?: return@create
            OpenFileAction.openFile(file.absolutePath, project)
        }
    )

    Notifications.Bus.notify(notification)
}
