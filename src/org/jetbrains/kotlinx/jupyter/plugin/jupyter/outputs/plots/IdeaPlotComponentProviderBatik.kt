// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import com.intellij.ui.components.JBScrollPane
import jetbrains.datalore.vis.swing.batik.DefaultPlotComponentProviderBatik
import javax.swing.JComponent
import javax.swing.JScrollPane

class IdeaPlotComponentProviderBatik(
    processedSpec: MutableMap<String, Any>,
    preserveAspectRatio: Boolean,
    executor: (() -> Unit) -> Unit,
    computationMessagesHandler: (List<String>) -> Unit
) : DefaultPlotComponentProviderBatik(
    processedSpec = processedSpec,
    preserveAspectRatio = preserveAspectRatio,
    executor = executor,
    computationMessagesHandler = computationMessagesHandler
) {

    override fun createScrollPane(plotComponent: JComponent): JScrollPane {
        return JBScrollPane(
            plotComponent,
            JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
    }
}
