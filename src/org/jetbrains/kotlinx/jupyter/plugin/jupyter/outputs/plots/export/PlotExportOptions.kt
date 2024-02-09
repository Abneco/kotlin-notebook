// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.DelegatingOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.prop
import org.jetbrains.kotlinx.jupyter.plugin.settings.propNarrowing
import java.util.*

enum class ExportFormat(val isRaster: Boolean = false) {
    SVG,
    PNG(true),
    JPG(true),
    HTML,
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
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.plot.export.title")
    }

    var format by prop(State::format)
        internal set

    var fileName: String by propNarrowing(State::fileName) { it ?: DEFAULT_FILE_NAME }
        internal set

    var scalingFactor: Double by prop(State::scalingFactor)
        internal set

    var targetDPI: Int by prop(State::targetDPI)
        internal set


    class State : BaseState() {
        var format: ExportFormat by enum(ExportFormat.SVG)
        var fileName: String? by string(DEFAULT_FILE_NAME)
        var scalingFactor by property(DEFAULT_SCALING_FACTOR, isDefault = {it == DEFAULT_SCALING_FACTOR})
        var targetDPI by property(4000)
    }

    interface Listener : EventListener

    companion object {
        fun getInstance(project: Project): PlotExportOptions = project.service()

        private const val DEFAULT_SCALING_FACTOR = 2.0
        private const val DEFAULT_FILE_NAME = "plot.svg"
    }
}
