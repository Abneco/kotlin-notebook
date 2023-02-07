// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.openapi.editor.impl.EditorImpl
import jetbrains.datalore.plot.MonolithicCommon
import jetbrains.datalore.vis.swing.batik.DefaultPlotPanelBatik
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinSerializationJson
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.createGutterPainter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.updateGutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import java.awt.Component
import javax.swing.JPanel

class LetsPlotOutputComponentFactory: NotebookOutputComponentFactory<LetsPlotOutputComponentFactory.LetsPlotComponent, LetsPlotOutputDataKey> {

    override val componentClass: Class<LetsPlotComponent>
        get() = LetsPlotComponent::class.java
    override val outputDataKeyClass: Class<LetsPlotOutputDataKey>
        get() = LetsPlotOutputDataKey::class.java

    override fun createComponent(
        editor: EditorImpl,
        output: LetsPlotOutputDataKey
    ): NotebookOutputComponentFactory.CreatedComponent<LetsPlotComponent>? {
        val component = LetsPlotComponent()
        component.initialize(output)
        return NotebookOutputComponentFactory.CreatedComponent(
            component,
            NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE,
            output.createGutterPainter(),
            false,
            false,
            { "LetsPlot plot" },
            null
        )
    }

    override fun updateComponent(editor: EditorImpl, component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey) {
        outputDataKey.updateGutterPainter(component.gutterPainter)
        component.initialize(outputDataKey)
    }

    override fun match(component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey): NotebookOutputComponentFactory.Match {
        if (component.dataKey == outputDataKey) {
            return NotebookOutputComponentFactory.Match.SAME
        }
        else {
            return NotebookOutputComponentFactory.Match.NONE
        }
    }

    class LetsPlotComponent : JPanel() {
        private var jComponent: DefaultPlotPanelBatik? = null

        private var _dataKey: LetsPlotOutputDataKey? = null

        val dataKey: LetsPlotOutputDataKey? get() = _dataKey
        fun initialize(dataKey: LetsPlotOutputDataKey) {
            val rawSpec = deserializeSpec(dataKey.spec.toKotlinSerializationJson()).toMutableMap()
            val processedSpec = MonolithicCommon.processRawSpecs(rawSpec, false)
            val plotPanel = DefaultPlotPanelBatik(
                processedSpec, false, true, 200
            ) { messages ->
                for (message in messages) {
                    println("[Demo Plot Viewer] $message")
                }
            }
            plotPanel.alignmentX = Component.CENTER_ALIGNMENT

            add(plotPanel)

            jComponent = plotPanel
            _dataKey = dataKey
        }
    }
}