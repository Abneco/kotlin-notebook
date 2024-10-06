// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.kotlin.jupyter.plots.LetsPlotFlavor
import com.intellij.kotlin.jupyter.plots.getCurrentLetsPlotFlavor
import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.settings.DelegatingOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.prop
import org.jetbrains.kotlinx.jupyter.plugin.settings.propNarrowing
import org.jetbrains.letsPlot.core.plot.export.PlotImageExport.buildImageFromRawSpecs
import java.util.*

enum class ExportFormat(
    val extension: String,
    val isRaster: Boolean = false,
) {
    SVG("svg"),
    PNG("png", true),
    JPG("jpg", true),
    TIFF("tiff", true),
    HTML("html"),
}

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookPlotExportOptionsProvider",
    presentableName = PlotExportOptions.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class PlotExportOptions :
    DelegatingOptionsProvider<PlotExportOptions.State, PlotExportOptions.Listener>(
        State(),
        Listener::class.java
    )
{
    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = KotlinNotebookPlotsBundle.message("kotlin.jupyter.settings.plot.export.title")
    }

    var format by prop(State::format)
        internal set

    var fileName: String by propNarrowing(State::fileName) { it ?: DEFAULT_FILE_NAME }
        internal set

    var letsPlotFlavor by prop(State::letsPlotFlavor)
        internal set

    var scalingFactor: Double by prop(State::scalingFactor)
        internal set

    var targetDPI: Int by prop(State::targetDPI)
        internal set

    fun restoreDefaults() {
        loadState(State())
    }


    class State : BaseState() {
        var format: ExportFormat by enum(ExportFormat.SVG)
        var fileName: String? by string(DEFAULT_FILE_NAME)
        var letsPlotFlavor: LetsPlotFlavor by enum(getCurrentLetsPlotFlavor())
        var scalingFactor by property(SCALING_FACTOR.default, isDefault = {it == SCALING_FACTOR.default})
        var targetDPI by property(TARGET_DPI.default)
    }

    interface Listener : EventListener

    companion object {
        fun getInstance(project: Project): PlotExportOptions = project.service()

        private const val DEFAULT_FILE_NAME = "plot.svg"
        /**
         * These values are taken from [buildImageFromRawSpecs].
         */
        val SCALING_FACTOR = RangeWithDefault(2.0, 0.1, 10.0)

        val TARGET_DPI = RangeWithDefault(4000, 72, 4000)
    }
}
