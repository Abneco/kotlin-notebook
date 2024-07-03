// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import java.util.MissingResourceException
import kotlin.reflect.KProperty

fun getSelectedKernelVersion(project: Project): String {
    return KotlinNotebookProjectOptionsProvider.getInstance(project).kernelVersion
}

val Project.selectedNotebookKernelVersion: KotlinKernelVersion?
    get() = KotlinKernelVersion.fromMavenVersion(getSelectedKernelVersion(this))

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

fun LanguageLevel.toCanonicalString() = toJavaVersion().toFeatureString()