// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.core.jupyter.outputs.export.createImageDataTransferable
import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey
import com.intellij.kotlin.jupyter.plots.updateFlavor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.letsPlot.awt.plot.PlotSvgExport
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@RequiresBackgroundThread
fun savePlot(
    plot: LetsPlotOutputDataKey,
    model: PlotExportModel,
    file: Path,
) {
    val content = exportPlot(plot, model)
    content.saveToFile(file)
}

@RequiresBackgroundThread
fun copyPlotToClipboard(
    project: Project,
    plot: LetsPlotOutputDataKey,
    model: PlotExportModel
) {
    val content = exportPlot(plot, model)
    val transferable = content.asTransferable(project)
    CopyPasteManager.getInstance().setContents(transferable)
}

private interface PlotContent {
    fun saveToFile(file: Path)
    fun asTransferable(project: Project): Transferable
}

private class TextPlotContent(private val text: String) : PlotContent {
    override fun saveToFile(file: Path) {
        file.writeText(text)
    }

    override fun asTransferable(project: Project): Transferable {
        return StringSelection(text)
    }
}

private class BinaryPlotContent(
    private val bytes: ByteArray,
) : PlotContent {
    override fun saveToFile(file: Path) {
        file.writeBytes(bytes)
    }

    override fun asTransferable(project: Project): Transferable {
        return createImageDataTransferable(bytes)
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
            val html = buildHtmlFromRawPlotSpec(spec)
            TextPlotContent(html)
        }
        else -> {
            val format = when (modelFormat) {
                ExportFormat.PNG -> PlotImageExport.Format.PNG
                ExportFormat.JPG -> PlotImageExport.Format.JPEG()
                ExportFormat.TIFF -> PlotImageExport.Format.TIFF
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
