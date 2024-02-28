// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.MutableLetsPlotSpec
import org.jetbrains.letsPlot.awt.plot.PlotSvgExport
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport
import org.jetbrains.letsPlot.core.util.PlotHtmlExport
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

interface PlotContent {
    fun saveToFile(file: File)
    fun asTransferable(): Transferable
}

class TextPlotContent(private val text: String) : PlotContent {
    override fun saveToFile(file: File) {
        file.writeText(text)
    }

    override fun asTransferable(): Transferable {
        return StringSelection(text)
    }
}

class BinaryPlotContent(private val bytes: ByteArray) : PlotContent {
    override fun saveToFile(file: File) {
        file.writeBytes(bytes)
    }

    override fun asTransferable(): Transferable {
        val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
        return BufferedImageTransferable(bufferedImage)
    }
}

@RequiresBackgroundThread
fun exportPlot(
    spec: MutableLetsPlotSpec,
    model: PlotExportModel,
): PlotContent {
    ThreadingAssertions.assertBackgroundThread()
    return when (val modelFormat = model.format) {
        ExportFormat.SVG -> {
            val svg = PlotSvgExport.buildSvgImageFromRawSpecs(spec)
            TextPlotContent(svg)
        }
        ExportFormat.HTML -> {
            val html = PlotHtmlExport.buildHtmlFromRawSpecs(spec, iFrame = true, scriptUrl = PlotHtmlHelper.scriptUrl("4.0.0"))
            TextPlotContent(html)
        }
        else -> {
            val format = when (modelFormat) {
                ExportFormat.PNG -> PlotImageExport.Format.PNG
                ExportFormat.JPG -> PlotImageExport.Format.JPEG()
                else -> throw IllegalStateException("No other formats are possible on this stage")
            }
            val byteArray = PlotImageExport.buildImageFromRawSpecs(
                plotSpec = spec,
                format = format,
                scalingFactor = model.scalingFactor,
                targetDPI = model.targetDPI.toDouble(),
            ).bytes
            BinaryPlotContent(byteArray)
        }
    }
}

@RequiresBackgroundThread
fun savePlot(
    spec: MutableLetsPlotSpec,
    model: PlotSaveModel,
): File {
    val content = exportPlot(spec, model)
    val file = File(model.directory, model.fileName)
    content.saveToFile(file)
    return file
}

private val defaultCopyPlotModel = PlotExportModelImpl(
    format = ExportFormat.PNG,
    scalingFactor = 2.0,
    targetDPI = 4000
)

@RequiresBackgroundThread
fun copyPlotToClipboard(
    spec: MutableLetsPlotSpec,
    model: PlotExportModel = defaultCopyPlotModel
) {
    val content = exportPlot(spec, model)
    val transferable = content.asTransferable()
    CopyPasteManager.getInstance().setContents(transferable)
}
