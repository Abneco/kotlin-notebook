// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.MINIMUM_SUPPORTED_VERSION
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.debugFeaturesEnabled
import java.util.*
import kotlin.reflect.KProperty

fun getSelectedKernelVersion(project: Project): String {
    return KotlinNotebookProjectOptionsProvider.getInstance(project).kernelVersion
}

val String.isKernelVersionEnoughForInstrumentation: Boolean
    get() {
        val comparator = KotlinKernelVersion.STRING_VERSION_COMPARATOR

        return comparator.compare(this, MINIMUM_SUPPORTED_VERSION) >= 0
    }

val Project.isKernelVersionEnoughForInstrumentation: Boolean
    get() = KotlinNotebookProjectOptionsProvider.getInstance(this)
        .kernelVersion.isKernelVersionEnoughForInstrumentation
            && debugFeaturesEnabled

internal class RegistryFlagDelegate(private val name: String, private val defaultValue: Boolean) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        // it's implemented this way to make it possible to set the registry key programmatically for tests
        return try {
            Registry.`is`(name)
        } catch (e: MissingResourceException) {
            defaultValue
        }
    }
}

internal fun registryFlag(
    name: String,
    defaultValue: Boolean
) = RegistryFlagDelegate(name, defaultValue)
