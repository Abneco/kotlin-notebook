// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.kotlin.jupyter.core.util.MouseEventDeepReDispatcher
import com.intellij.kotlin.jupyter.core.util.RetargetingCursorProvider
import com.intellij.kotlin.jupyter.core.util.addCursorProvider
import com.intellij.kotlin.jupyter.core.util.addDispatchingMouseListener
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLayeredPane
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.ggdsl.util.serialization.deserializeSpec
import org.jetbrains.letsPlot.awt.plot.component.PlotPanel
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.spec.config.PlotConfig
import org.jetbrains.letsPlot.core.spec.front.PlotConfigFrontend
import org.jetbrains.letsPlot.core.util.MonolithicCommon
import org.jetbrains.letsPlot.core.util.PlotHtmlExport
import org.jetbrains.letsPlot.core.util.PlotHtmlHelper
import org.jetbrains.letsPlot.core.util.PlotSizeHelper
import org.jetbrains.letsPlot.core.util.sizing.SizingPolicy
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.math.roundToInt

class LetsPlotComponent : JBLayeredPane() {
    private var plotPanel: PlotPanel? = null
    private var transparentPanel: JPanel? = null
    private var _dataKey: LetsPlotOutputDataKey? = null
    private var previousColorFlavor: LetsPlotFlavor? = null

    val dataKey: LetsPlotOutputDataKey? get() = _dataKey

    fun initialize(dataKey: LetsPlotOutputDataKey) {
        reinitComponent(getSpec(dataKey))
        _dataKey = dataKey
    }

    override fun updateUI() {
        val colorFlavor = getCurrentLetsPlotFlavor()
        val data = dataKey ?: return
        if (previousColorFlavor == colorFlavor) return
        previousColorFlavor = colorFlavor

        reinitComponent(getSpec(data, colorFlavor))
    }

    override fun doLayout() {
        super.doLayout()
        val mySize = size
        if (mySize.width <= 0 || mySize.height <= 0) return

        val myComponent = plotPanel ?: return
        val myData = dataKey ?: return
        val spec = getSpec(myData)
        val plotSize = plotSize(spec, mySize, SizingPolicy.fitContainerSize(true)) ?: mySize
        myComponent.bounds = Rectangle(plotSize)
        // This is a workaround: a plot panel may skip first resize event, but we need it to rebuild the plot
        myComponent.dispatchEvent(ComponentEvent(myComponent, ComponentEvent.COMPONENT_RESIZED))

        val transparentPanel = this.transparentPanel ?: return
        transparentPanel.size = mySize
    }

    override fun getPreferredSize(): Dimension {
        return dataKey?.let {
            plotSize(getSpec(it), parent?.size, SizingPolicy.notebookCell())
        } ?: plotPanel?.preferredSize ?: super.getPreferredSize()
    }

    private fun reinitComponent(spec: MutableLetsPlotSpec) {
        clear()
        initForSpec(spec)
    }

    private fun clear() {
        removeAll()
        @Suppress("SSBasedInspection")
        plotPanel?.dispose()
        plotPanel = null
        transparentPanel = null
    }

    private fun initForSpec(processedSpec: MutableLetsPlotSpec) {
        val plotComponentProvider = IdeaPlotComponentProviderBatik(
            processedSpec = processedSpec,
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
            applicationContext = IdeaSwingContextBatik,
            sizingPolicy = SizingPolicy.fitContainerSize(true)
        ){}

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

    @TestOnly
    fun getPlotHtml() = dataKey?.let {
        PlotHtmlExport.buildHtmlFromRawSpecs(
            deserializeSpec(it.spec).toMutableMap(),
            iFrame = true,
            scriptUrl = PlotHtmlHelper.scriptUrl("4.0.0")
        )
    } ?: ""

    companion object {
        private val LOG = thisLogger()
    }
}

private fun getSpec(dataKey: LetsPlotOutputDataKey) = getSpec(dataKey, getCurrentLetsPlotFlavor())
private fun getSpec(dataKey: LetsPlotOutputDataKey, flavor: LetsPlotFlavor): MutableLetsPlotSpec {
    val rawSpec = deserializeSpec(dataKey.spec).toMutableMap().also {
        if (dataKey.applyColorScheme) {
            updateFlavor(it, flavor)
        }
    }
    val processedSpec = MonolithicCommon.processRawSpecs(rawSpec, false)
    return processedSpec.toMutableMap()
}

private fun plotSize(spec: LetsPlotSpec, containerSize: Dimension?, sizingPolicy: SizingPolicy): Dimension? {
    if (PlotConfig.isFailure(spec)) {
        // Keep given size
        return containerSize
    }

    val config = PlotConfigFrontend.create(spec) {}
    val containerSizeVec = containerSize?.run { DoubleVector(width, height) }
    val plotSize = PlotSizeHelper.singlePlotSize(spec, containerSizeVec, sizingPolicy, config.facets, config.containsLiveMap)
    return plotSize.run { Dimension(x.roundToInt(),y.roundToInt()) }
}
