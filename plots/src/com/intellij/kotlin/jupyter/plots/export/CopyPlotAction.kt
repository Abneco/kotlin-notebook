// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.runSafely
import com.intellij.kotlin.jupyter.plots.LetsPlotOutputDataKey
import com.intellij.kotlin.jupyter.plots.getCurrentLetsPlotFlavor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.async

class CopyPlotAction : AbstractExportPlotAction() {
    override fun isActionApplicable(outputs: List<LetsPlotOutputDataKey>): Boolean {
        return outputs.size == 1
    }

    override fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: VirtualFile
    ) {
        val output = letsPlotOutputs.singleOrNull() ?: return
        KotlinNotebookPluginScope.getForProject(project).async {
            runSafely (
                {
                    val model = PlotExportModelImpl(
                        format = ExportFormat.PNG,
                        scalingFactor = PlotExportOptions.SCALING_FACTOR.default,
                        targetDPI = PlotExportOptions.TARGET_DPI.default,
                        letsPlotFlavor = getCurrentLetsPlotFlavor()
                    )
                    copyPlotToClipboard(output, model)
                },
                { throwable ->
                    showPlotExportFailedNotification(throwable)
                }
            )
        }
    }
}
