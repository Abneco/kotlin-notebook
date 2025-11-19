// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

data class LetsPlotComponentConfiguredState(
    val dataKey: LetsPlotOutputDataKey?,
    val colorFlavor: LetsPlotFlavor,
    val showToolbar: Boolean,
)
