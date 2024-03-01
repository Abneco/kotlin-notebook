// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.updateFlavor
import org.jetbrains.letsPlot.awt.plot.PlotSvgExport
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport
import org.jetbrains.letsPlot.core.util.PlotHtmlExport
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

@RequiresBackgroundThread
fun savePlot(
    plot: LetsPlotOutputDataKey,
    model: PlotSaveModel,
): File {
    val content = exportPlot(plot, model)
    val file = File(model.directory, model.fileName)
    content.saveToFile(file)
    return file
}

@RequiresBackgroundThread
fun copyPlotToClipboard(
    plot: LetsPlotOutputDataKey,
    model: PlotExportModel
) {
    val content = exportPlot(plot, model)
    val transferable = content.asTransferable()
    CopyPasteManager.getInstance().setContents(transferable)
}

private interface PlotContent {
    fun saveToFile(file: File)
    fun asTransferable(): Transferable
}

private class TextPlotContent(private val text: String) : PlotContent {
    override fun saveToFile(file: File) {
        file.writeText(text)
    }

    override fun asTransferable(): Transferable {
        return StringSelection(text)
    }
}

private class BinaryPlotContent(private val bytes: ByteArray) : PlotContent {
    override fun saveToFile(file: File) {
        file.writeBytes(bytes)
    }

    override fun asTransferable(): Transferable {
        val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
        return BufferedImageTransferable(bufferedImage)
    }
}

@RequiresBackgroundThread
private fun exportPlot(
    plot: LetsPlotOutputDataKey,
    model: PlotExportModel,
): PlotContent {
    ThreadingAssertions.assertBackgroundThread()

    val spec = plot.toMutableSpec()
    updateFlavor(spec, model.letsPlotFlavor)

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

private fun LetsPlotOutputDataKey.toMutableSpec() =
    deserializeSpec(spec).toMutableMap()
