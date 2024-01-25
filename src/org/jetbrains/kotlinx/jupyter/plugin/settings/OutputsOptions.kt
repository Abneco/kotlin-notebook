// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

internal val letsPlotSwingOutputsEnabled: Boolean by registryFlag("lets.plot.swing.outputs.enabled", true)
internal val isSwingUiEnabledForKotlinDataframe: Boolean by registryFlag("kotlin.dataframe.swing.outputs.enabled", true)
