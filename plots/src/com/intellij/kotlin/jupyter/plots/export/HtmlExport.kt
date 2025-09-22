// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.MutableLetsPlotSpec
import org.jetbrains.letsPlot.core.util.PlotHtmlExport
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import org.jetbrains.letsPlot.export.VersionChecker

fun buildHtmlFromRawPlotSpec(
    spec: MutableLetsPlotSpec,
): String {
    return PlotHtmlExport.buildHtmlFromRawSpecs(
        spec,
        iFrame = true,
        scriptUrl = PlotHtmlHelper.scriptUrl(VersionChecker.letsPlotJsVersion)
    )
}
