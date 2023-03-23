// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.EditorImpl
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
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinSerializationJson
import org.jetbrains.kotlinx.jupyter.plugin.util.uiFeelsDark
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.createGutterPainter
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.updateGutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import java.awt.Color
import java.awt.Component
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

            if (spec.isGGBunch) return

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
                    preserveAspectRatio = true,
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
            plotPanel.background = plotBackground(processedSpec)

            alignmentX = Component.LEFT_ALIGNMENT

            add(plotPanel)
            jComponent = plotPanel
        }
    }

    companion object {
        private val LOG = thisLogger()

        private fun getSpec(dataKey: LetsPlotOutputDataKey) = getSpec(dataKey, uiFeelsDark())

        private fun getSpec(dataKey: LetsPlotOutputDataKey, isDark: Boolean?): MutableLetsPlotSpec {
            val rawSpec = deserializeSpec(dataKey.spec.toKotlinSerializationJson()).toMutableMap()
            val flavoredSpec = updateFlavorForSinglePlot(rawSpec, isDark)
            val processedSpec = MonolithicCommon.processRawSpecs(flavoredSpec, false)
            return processedSpec.toMutableMap()
        }

        private val LetsPlotSpec.isGGBunch: Boolean get() = !PlotConfig.isFailure(this)
                && PlotConfig.figSpecKind(this) == FigKind.GG_BUNCH_SPEC

        private fun updateFlavorForSinglePlot(rawSpec: MutableLetsPlotSpec, isDark: Boolean?) : MutableLetsPlotSpec {
            if (isDark == null) return rawSpec

            val themeMap = rawSpec.compute(Option.Plot.THEME) { _, prevVal ->
                if (prevVal == null || prevVal !is Map<*, *>) mutableMapOf<String, Any>()
                else prevVal.toMutableMap()
            }

            @Suppress("UNCHECKED_CAST")
            themeMap as MutableLetsPlotSpec

            themeMap[Option.Theme.FLAVOR] = if (isDark) ThemeOption.Flavor.DARCULA else ThemeOption.Flavor.HIGH_CONTRAST_LIGHT
            return rawSpec
        }

        private fun plotBackground(processedSpec: LetsPlotSpec): Color {
            val themeOptions = themeOptions(processedSpec)
            val theme = ThemeConfig(themeOptions, DefaultFontFamilyRegistry()).theme
            val c = theme.plot().backgroundFill()
            @Suppress("UseJBColor")
            return Color(c.red, c.green, c.blue)
        }

        private fun themeOptions(spec: LetsPlotSpec): LetsPlotSpec {
            val themeOptions = spec[Option.Plot.THEME]?.let {
                @Suppress("UNCHECKED_CAST")
                if (it is Map<*, *>) it as LetsPlotSpec
                else emptyMap()
            } ?: emptyMap()
            return themeOptions
        }

        private fun plotSizeCropped(spec: LetsPlotSpec, containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
            return plotSize(spec, (containerWidth - 10).coerceAtLeast(0), (containerHeight - 10).coerceAtLeast(0))
        }


        private fun plotSize(spec: LetsPlotSpec, containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
            val userSize = userPlotSize(spec)?.asIntPair()
            if (userSize != null && userSize.first <= containerWidth && userSize.second <= containerHeight) {
                return userSize
            }

            return PlotSizeHelper.scaledFigureSize(spec, containerWidth, containerHeight)
        }

        private fun userPlotSize(processedSpec: LetsPlotSpec): DoubleVector? {
            val sizeOptions = processedSpec[Option.Plot.SIZE]?.let {
                @Suppress("UNCHECKED_CAST")
                if (it is Map<*, *>) it as LetsPlotSpec
                else null
            } ?: return null

            val width = sizeOptions[Option.Plot.WIDTH] as? Number ?: return null
            val height = sizeOptions[Option.Plot.HEIGHT] as? Number ?: return null

            return DoubleVector(width.toDouble(), height.toDouble())
        }

        private fun DoubleVector.asIntPair(): Pair<Int, Int> {
            return Pair(ceil(x).toInt(), ceil(y).toInt())
        }
    }
}
