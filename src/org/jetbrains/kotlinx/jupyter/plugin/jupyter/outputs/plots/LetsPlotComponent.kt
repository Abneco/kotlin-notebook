// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.asSafely
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.kotlinx.jupyter.plugin.util.*
import org.jetbrains.letsPlot.awt.plot.component.PlotPanel
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.values.ThemeOption
import org.jetbrains.letsPlot.core.spec.FigKind
import org.jetbrains.letsPlot.core.spec.config.PlotConfig
import org.jetbrains.letsPlot.core.util.MonolithicCommon
import org.jetbrains.letsPlot.core.util.PlotSizeHelper
import java.awt.Dimension
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.floor

class LetsPlotComponent : JBLayeredPane() {
    private var plotPanel: JComponent? = null
    private var transparentPanel: JPanel? = null
    private var _dataKey: LetsPlotOutputDataKey? = null
    private var previousIsDark: Boolean? = null

    val dataKey: LetsPlotOutputDataKey? get() = _dataKey

    fun initialize(dataKey: LetsPlotOutputDataKey) {
        reinitComponent(getSpec(dataKey))
        _dataKey = dataKey
    }

    override fun updateUI() {
        val isDark = uiFeelsDark()
        val data = _dataKey ?: return
        if (previousIsDark == isDark) return
        previousIsDark = isDark

        reinitComponent(getSpec(data, isDark))
    }

    override fun doLayout() {
        super.doLayout()
        val mySize = size
        if (mySize.width <= 0 || mySize.height <= 0) return

        val myComponent = plotPanel ?: return
        val myData = dataKey ?: return
        val spec = getSpec(myData)
        val (plotWidth, plotHeight) = plotSize(spec, mySize.width, mySize.height)
        myComponent.setBounds(0, 0, plotWidth, plotHeight)
        // This is a workaround: plot panel may skip first resize event, but we need it to rebuild the plot
        myComponent.dispatchEvent(ComponentEvent(myComponent, ComponentEvent.COMPONENT_RESIZED))

        val transparentPanel = this.transparentPanel ?: return
        transparentPanel.size = mySize
    }

    override fun getPreferredSize(): Dimension {
        return plotPanel?.preferredSize ?: super.getPreferredSize()
    }

    private fun reinitComponent(spec: MutableLetsPlotSpec) {
        clear()
        initForSpec(spec)
    }

    private fun clear() {
        removeAll()
        plotPanel = null
        transparentPanel = null
    }

    private fun initForSpec(processedSpec: MutableLetsPlotSpec) {
        val plotComponentProvider = IdeaPlotComponentProviderBatik(
            processedSpec = processedSpec,
            preserveAspectRatio = false,
            executor = IdeaSwingContextBatik.IDEA_EDT_EXECUTOR,
            computationMessagesHandler = { messages ->
                for (message in messages) {
                    LOG.debug("[Demo Plot Viewer] $message")
                }
            }
        )

        val plotPanel: PlotPanel = object : PlotPanel(
            plotComponentProvider = plotComponentProvider,
            preferredSizeFromPlot = true,
            repaintDelay = 200,
            applicationContext = IdeaSwingContextBatik
        ), Disposable {}

        plotPanel.isOpaque = true

        alignmentX = CENTER_ALIGNMENT
        alignmentY = CENTER_ALIGNMENT

        val transparentPanel = JPanel().apply {
            isOpaque = false
            PopupHandler.installPopupMenu(this, "LetsPlotActions", ActionPlaces.JUPYTER_NOTEBOOK_CELL_OUTPUT_POPUP)

            addDispatchingMouseListener(
                MouseEventDeepReDispatcher(plotPanel) { e: MouseEvent ->
                    when (e.id) {
                        MouseEvent.MOUSE_CLICKED,
                        MouseEvent.MOUSE_PRESSED,
                        MouseEvent.MOUSE_RELEASED -> false
                        else -> true
                    }
                }
            )

            addCursorProvider(
                RetargetingCursorProvider.Factory(plotPanel)
            )
        }

        add(transparentPanel, POPUP_LAYER, -1)
        add(plotPanel, DEFAULT_LAYER, -1)

        this.plotPanel = plotPanel
        this.transparentPanel = transparentPanel
    }

    companion object {
        private val LOG = thisLogger()
    }
}

private fun getSpec(dataKey: LetsPlotOutputDataKey) = getSpec(dataKey, uiFeelsDark())
private fun getSpec(dataKey: LetsPlotOutputDataKey, isDark: Boolean): MutableLetsPlotSpec {
    val rawSpec = deserializeSpec(dataKey.spec).toMutableMap().also {
        if (dataKey.applyColorScheme) {
            updateFlavor(it, isDark)
        }
    }
    val processedSpec = MonolithicCommon.processRawSpecs(rawSpec, false)
    return processedSpec.toMutableMap()
}

private fun updateFlavorForPlot(spec: MutableLetsPlotSpec, flavorName: String) {
    spec.compute("theme") { _, theme ->
        (theme.asSafely<LetsPlotSpec>()?.toMutableMap() ?: mutableMapOf()).apply {
            putIfAbsent("flavor", flavorName)
        }
    }
}

private fun updateFlavorForGGBunch(spec: MutableLetsPlotSpec, flavorName: String) {
    spec.compute("items") { _, items ->
        (items.asSafely<List<LetsPlotSpec>>())?.map {
            it.toMutableMap().also { item ->
                item.compute("feature_spec") { _, feat ->
                    @Suppress("UNCHECKED_CAST") (feat as LetsPlotSpec).toMutableMap().also { plotSpec ->
                        updateFlavorForPlot(plotSpec, flavorName)
                    }
                }
            }
        }.orEmpty()
    }
}

private fun updateFlavorForSubPlots(spec: MutableLetsPlotSpec, flavorName: String) {
    spec.compute("figures") { _, figures ->
        (figures.asSafely<List<LetsPlotSpec?>>())?.map {
            it?.toMutableMap()?.also { figure ->
                updateFlavorForPlot(figure, flavorName)
            }
        }.orEmpty()
    }
}

private fun updateFlavor(rawSpec: MutableLetsPlotSpec, isDark: Boolean) {
    val flavorName = if (isDark) ThemeOption.Flavor.DARCULA else ThemeOption.Flavor.HIGH_CONTRAST_LIGHT
    when (PlotConfig.figSpecKind(rawSpec)) {
        FigKind.PLOT_SPEC -> updateFlavorForPlot(rawSpec, flavorName)
        FigKind.SUBPLOTS_SPEC -> updateFlavorForSubPlots(rawSpec, flavorName)
        FigKind.GG_BUNCH_SPEC -> updateFlavorForGGBunch(rawSpec, flavorName)
        else -> return
    }
}

private fun plotSize(spec: LetsPlotSpec, containerWidth: Int, containerHeight: Int): Pair<Int, Int> {
    return scaledFigureSize(spec, containerWidth, containerHeight)
}

private fun scaledFigureSize(
    aspectRatio: Double, containerWidth: Int, containerHeight: Int
): Pair<Int, Int> {
    return if (aspectRatio >= 1.0) {
        val plotHeight = containerWidth / aspectRatio
        val scaling = if (plotHeight > containerHeight) containerHeight / plotHeight else 1.0
        Pair(floor(containerWidth * scaling).toInt(), floor(plotHeight * scaling).toInt())
    } else {
        val plotWidth = containerHeight * aspectRatio
        val scaling = if (plotWidth > containerWidth) containerWidth / plotWidth else 1.0
        Pair(floor(plotWidth * scaling).toInt(), floor(containerHeight * scaling).toInt())
    }
}

private fun scaledFigureSize(
    figureSpec: LetsPlotSpec, containerWidth: Int, containerHeight: Int
): Pair<Int, Int> {

    if (PlotConfig.isFailure(figureSpec)) { // just keep given size
        return Pair(containerWidth, containerHeight)
    }

    return when (PlotConfig.figSpecKind(figureSpec)) {
        FigKind.GG_BUNCH_SPEC -> {
            // don't scale GGBunch size
            val bunchSize = PlotSizeHelper.plotBunchSize(figureSpec)
            Pair(ceil(bunchSize.x).toInt(), ceil(bunchSize.y).toInt())
        }

        FigKind.PLOT_SPEC -> {
            // for single plot: scale component to fit in requested size
            val aspectRatio = PlotSizeHelper.figureAspectRatio(figureSpec)
            scaledFigureSize(aspectRatio, containerWidth, containerHeight)
        }

        FigKind.SUBPLOTS_SPEC -> {
            val (nCol, nRow) = (figureSpec["layout"]!! as Map<*, *>).let {
                (it["ncol"]!! as Double) to (it["nrow"] as Double)
            }
            val aspectRatio = (nCol * 600.0) / (nRow * 400.0)
            scaledFigureSize(aspectRatio, containerWidth, containerHeight)
        }
    }
}
