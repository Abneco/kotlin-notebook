// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.JBColor
import jetbrains.datalore.base.geometry.DoubleVector
import jetbrains.datalore.plot.MonolithicCommon
import jetbrains.datalore.plot.PlotSizeHelper
import jetbrains.datalore.plot.builder.defaultTheme.values.ThemeOption
import jetbrains.datalore.plot.builder.presentation.DefaultFontFamilyRegistry
import jetbrains.datalore.plot.config.FigKind
import jetbrains.datalore.plot.config.Option
import jetbrains.datalore.plot.config.PlotConfig
import jetbrains.datalore.plot.config.theme.ThemeConfig
import jetbrains.datalore.vis.swing.PlotPanel
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.util.uiFeelsDark
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.createGutterPainter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.updateGutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil

class LetsPlotOutputComponentFactory: NotebookOutputComponentFactory<LetsPlotOutputComponentFactory.LetsPlotComponent, LetsPlotOutputDataKey> {

    override val componentClass: Class<LetsPlotComponent>
        get() = LetsPlotComponent::class.java
    override val outputDataKeyClass: Class<LetsPlotOutputDataKey>
        get() = LetsPlotOutputDataKey::class.java

    override fun createComponent(
        editor: EditorImpl,
        output: LetsPlotOutputDataKey
    ): NotebookOutputComponentFactory.CreatedComponent<LetsPlotComponent> {
        val component = LetsPlotComponent()
        component.initialize(output)
        return NotebookOutputComponentFactory.CreatedComponent(
            component,
            NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE,
            output.createGutterPainter(),
            limitHeight = false,
            resizable = true,
            { "LetsPlot plot" },
            null
        )
    }

    override fun updateComponent(editor: EditorImpl, component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey) {
        outputDataKey.updateGutterPainter(component.gutterPainter)
        component.initialize(outputDataKey)
    }

    override fun match(component: LetsPlotComponent, outputDataKey: LetsPlotOutputDataKey): NotebookOutputComponentFactory.Match {
        return if (component.dataKey == outputDataKey) {
            NotebookOutputComponentFactory.Match.SAME
        } else {
            NotebookOutputComponentFactory.Match.NONE
        }
    }

    class LetsPlotComponent : JPanel() {
        private var jComponent: JComponent? = null
        private var _dataKey: LetsPlotOutputDataKey? = null
        private var previousIsDark: Boolean? = null

        val dataKey: LetsPlotOutputDataKey? get() = _dataKey

        override fun updateUI() {
            val isDark = uiFeelsDark() ?: return
            val data = _dataKey ?: return
            if (previousIsDark == isDark) return
            previousIsDark = isDark

            clear()

            val spec = getSpec(data, isDark)
            initForSpec(spec)
        }

        override fun doLayout() {
            super.doLayout()
            val mySize = size
            if (mySize.width <= 0 || mySize.height <= 0) return

            val myComponent = jComponent ?: return
            val myData = dataKey ?: return
            val spec = getSpec(myData)
            val (plotWidth, plotHeight) = plotSizeCropped(spec, mySize.width, mySize.height)
            myComponent.setBounds(0, 0, plotWidth, plotHeight)
        }

        fun initialize(dataKey: LetsPlotOutputDataKey) {
            clear()
            val processedSpec = getSpec(dataKey)
            initForSpec(processedSpec)
            _dataKey = dataKey
        }

        private fun clear() {
            jComponent?.let {
                remove(it)
            }
        }

        private fun initForSpec(processedSpec: MutableLetsPlotSpec) {
            val plotPanel: PlotPanel = object : PlotPanel(
                plotComponentProvider = IdeaPlotComponentProviderBatik(
                    processedSpec = processedSpec,
                    preserveAspectRatio = false,
                    executor = IdeaSwingContextBatik.IDEA_EDT_EXECUTOR,
                    computationMessagesHandler = { messages ->
                        for (message in messages) {
                            LOG.debug("[Demo Plot Viewer] $message")
                        }
                    }
                ),
                preferredSizeFromPlot = true,
                repaintDelay = 200,
                applicationContext = IdeaSwingContextBatik
            ), Disposable {}

            plotPanel.isOpaque = true

            alignmentX = Component.CENTER_ALIGNMENT
            alignmentY = Component.CENTER_ALIGNMENT

            add(plotPanel)
            jComponent = plotPanel
        }
    }

    companion object {
        private val LOG = thisLogger()

        private fun getSpec(dataKey: LetsPlotOutputDataKey) = getSpec(dataKey, uiFeelsDark())

        private fun getSpec(dataKey: LetsPlotOutputDataKey, isDark: Boolean?): MutableLetsPlotSpec {
            val rawSpec = deserializeSpec(dataKey.spec).toMutableMap().also {
                if (dataKey.applyColorScheme) {
                    updateFlavor(it, isDark)
                }
            }
            val processedSpec = MonolithicCommon.processRawSpecs(rawSpec, false)
            return processedSpec.toMutableMap()
        }

        @Suppress("UNCHECKED_CAST")
        private fun updateFlavorForPlot(spec: MutableLetsPlotSpec, flavorName: String) {
            spec.compute("theme") { _, theme ->
                ((theme as? LetsPlotSpec)?.toMutableMap() ?: mutableMapOf()).apply {
                    putIfAbsent("flavor", flavorName)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun updateFlavorForGGBunch(spec: MutableLetsPlotSpec, flavorName: String) {
            spec.compute("items") { _, items ->
                (items as? List<LetsPlotSpec>)?.map {
                    it.toMutableMap().also { item ->
                        item.compute("feature_spec") { _, feat ->
                            (feat as LetsPlotSpec).toMutableMap().also { plotSpec ->
                                updateFlavorForPlot(plotSpec, flavorName)
                            }
                        }
                    }
                }.orEmpty()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun updateFlavorForSubPlots(spec: MutableLetsPlotSpec, flavorName: String) {
            spec.compute("figures") { _, figures ->
                (figures as? List<LetsPlotSpec?>)?.map {
                    it?.toMutableMap()?.also { figure ->
                        updateFlavorForPlot(figure, flavorName)
                    }
                }.orEmpty()
            }
        }

        private fun updateFlavor(rawSpec: MutableLetsPlotSpec, isDark: Boolean?)  {
            if (isDark == null) return
            val flavorName = if (isDark) ThemeOption.Flavor.DARCULA else ThemeOption.Flavor.HIGH_CONTRAST_LIGHT
            when(PlotConfig.figSpecKind(rawSpec)) {
                FigKind.PLOT_SPEC -> updateFlavorForPlot(rawSpec, flavorName)
                FigKind.SUBPLOTS_SPEC -> updateFlavorForSubPlots(rawSpec, flavorName)
                FigKind.GG_BUNCH_SPEC -> updateFlavorForGGBunch(rawSpec, flavorName)
                else -> return
            }
        }

        private fun plotSizeCropped(spec: LetsPlotSpec, containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
            return plotSize(spec, (containerWidth - 10).coerceAtLeast(0), (containerHeight - 10).coerceAtLeast(0))
        }


        private fun plotSize(spec: LetsPlotSpec, containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
            return PlotSizeHelper.scaledFigureSize(spec, containerWidth, containerHeight)
        }

    }
}
