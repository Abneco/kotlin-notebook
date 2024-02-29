// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.LetsPlotOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.getCurrentLetPlotFlavor
import org.jetbrains.kotlinx.jupyter.plugin.util.runSafely
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

class CopyPlotAction : AbstractExportPlotAction() {
    override fun doExport(
        letsPlotOutputs: List<LetsPlotOutputDataKey>,
        project: Project,
        notebookFile: BackedNotebookVirtualFile
    ) {
        val output = letsPlotOutputs.singleOrNull() ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            runSafely (
                {
                    val model = PlotExportModelImpl(
                        format = ExportFormat.PNG,
                        scalingFactor = 2.0,
                        targetDPI = 4000,
                        letsPlotFlavor = getCurrentLetPlotFlavor()
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
