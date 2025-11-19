// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import org.jetbrains.letsPlot.core.spec.Option
import org.jetbrains.letsPlot.toolkit.json.JsonMap

private const val TOOLBAR_OPTION: String = Option.Meta.Kind.GG_TOOLBAR

val JsonMap.isToolbarEnabled: Boolean
    get() = containsKey(TOOLBAR_OPTION)

fun configureToolbar(
    spec: MutableLetsPlotSpec,
    showToolbar: Boolean?,
) {
    when (showToolbar) {
        true -> spec.putIfAbsent(TOOLBAR_OPTION, emptyMap<Any, Any>())
        false -> spec.remove(TOOLBAR_OPTION)
        null -> {}
    }
}