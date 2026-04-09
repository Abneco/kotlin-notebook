// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import java.util.MissingResourceException
import kotlin.reflect.KProperty

const val APP_CONFIG_FILE: String = "kotlinNotebookApp.xml"
const val APP_LOCAL_CONFIG_FILE: String = "kotlinNotebookAppLocal.xml"

val Project.selectedKernelVersionAsString: String get() {
    return KotlinNotebookProjectOptionsProvider.getInstance(this).kernelVersion
}

val Project.selectedKernelVersion: KotlinKernelVersion?
    get() = KotlinKernelVersion.fromMavenVersion(selectedKernelVersionAsString)

class RegistryFlagDelegate(private val name: String, private val defaultValue: Boolean) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        // it's implemented this way to make it possible to set the registry key programmatically for tests
        return try {
            Registry.`is`(name)
        } catch (_: MissingResourceException) {
            defaultValue
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        Registry.get(name).setValue(value)
    }
}

fun registryFlag(
    name: String,
    defaultValue: Boolean
): RegistryFlagDelegate = RegistryFlagDelegate(name, defaultValue)

fun LanguageLevel.toCanonicalString(): String = toJavaVersion().toFeatureString()