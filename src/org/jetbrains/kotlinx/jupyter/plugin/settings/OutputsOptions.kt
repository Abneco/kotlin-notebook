// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.util.registry.Registry
import java.util.*
import kotlin.reflect.KProperty

internal val letsPlotSwingOutputsEnabled: Boolean by registryFlag("lets.plot.swing.outputs.enabled", true)
internal val isSwingUiEnabledForKotlinDataframe: Boolean by registryFlag("kotlin.dataframe.swing.outputs.enabled", true)

private fun registryFlag(name: String, @Suppress("SameParameterValue") defaultValue: Boolean) = object {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        // it's implemented this way to make it possible to set the registry key programmatically for tests
        return try {
            Registry.`is`(name)
        } catch (e: MissingResourceException) {
            defaultValue
        }
    }
}
