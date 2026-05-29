// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.ui.components.JBScrollPane
import org.jetbrains.letsPlot.awt.plot.swing.SwingPlotComponentProvider
import org.jetbrains.letsPlot.core.plot.builder.interact.tools.SpecOverrideState
import org.jetbrains.letsPlot.core.util.sizing.SizingPolicy
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JScrollPane

class IdeaPlotComponentProviderBatik(
    processedSpec: MutableMap<String, Any>,
    executor: (() -> Unit) -> Unit,
    computationMessagesHandler: (List<String>) -> Unit,
    private val componentCustomizer: (JComponent) -> Unit = {},
) : SwingPlotComponentProvider(
    processedSpec = processedSpec,
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

    override fun createComponent(containerSize: Dimension?, sizingPolicy: SizingPolicy, specOverrideState: SpecOverrideState): JComponent {
        return super.createComponent(containerSize, sizingPolicy, specOverrideState).also(componentCustomizer)
    }
}
