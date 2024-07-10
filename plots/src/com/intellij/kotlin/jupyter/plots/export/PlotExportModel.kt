// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.LetsPlotFlavor


interface PlotExportModel {
    val format: ExportFormat
    val scalingFactor: Double
    val targetDPI: Int
    val letsPlotFlavor: LetsPlotFlavor
}

class PlotExportModelImpl(
    override val format: ExportFormat,
    override val scalingFactor: Double,
    override val targetDPI: Int,
    override val letsPlotFlavor: LetsPlotFlavor,
): PlotExportModel
