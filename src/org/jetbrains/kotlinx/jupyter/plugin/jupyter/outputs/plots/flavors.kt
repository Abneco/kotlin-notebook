// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots

import com.intellij.openapi.util.NlsContexts
import com.intellij.util.asSafely
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.util.uiFeelsDark
import org.jetbrains.letsPlot.core.plot.builder.defaultTheme.values.ThemeOption
import org.jetbrains.letsPlot.core.spec.FigKind
import org.jetbrains.letsPlot.core.spec.config.PlotConfig

@Suppress("unused")
enum class LetsPlotFlavor(
    val flavorName: String,
    @NlsContexts.ListItem val description: String,
) {
    DARCULA(ThemeOption.Flavor.DARCULA, KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.darcula")),
    HIGH_CONTRAST_LIGHT(ThemeOption.Flavor.HIGH_CONTRAST_LIGHT, KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.highContrastLight")),
    HIGH_CONTRAST_DARK(ThemeOption.Flavor.HIGH_CONTRAST_DARK, KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.highContrastDark")),
    SOLARIZED_LIGHT(ThemeOption.Flavor.SOLARIZED_LIGHT, KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.solarizedLight")),
    SOLARIZED_DARK(ThemeOption.Flavor.SOLARIZED_DARK, KotlinNotebookBundle.message("kotlin.jupyter.dialog.outputs.plot.export.theme.solarizedDark")),
}

fun getCurrentLetPlotFlavor() = getLetPlotFlavor(uiFeelsDark())

fun getLetPlotFlavor(isDark: Boolean): LetsPlotFlavor {
    return if (isDark) LetsPlotFlavor.DARCULA else LetsPlotFlavor.HIGH_CONTRAST_LIGHT
}

fun updateFlavor(rawSpec: MutableLetsPlotSpec, flavor: LetsPlotFlavor) {
    val flavorName = flavor.flavorName
    when (PlotConfig.figSpecKind(rawSpec)) {
        FigKind.PLOT_SPEC -> updateFlavorForPlot(rawSpec, flavorName)
        FigKind.SUBPLOTS_SPEC -> updateFlavorForSubPlots(rawSpec, flavorName)
        FigKind.GG_BUNCH_SPEC -> updateFlavorForGGBunch(rawSpec, flavorName)
        else -> return
    }
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
