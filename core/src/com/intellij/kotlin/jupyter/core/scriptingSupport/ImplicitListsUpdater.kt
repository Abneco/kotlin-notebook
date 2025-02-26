// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import java.nio.file.Path
import kotlin.script.experimental.api.KotlinType


internal data class ClassPathSnippetsLoadedData(
    val path: Path,
    val snippetTypes: List<KotlinType>
)

/**
 * Interface used to distinguish concepts of a recently loaded receiver from runtime
 * and class ready to be added to the configuration for the particular notebook.
 *
 * In the general case scenario, these moments might be separated in time.
 */
internal interface ImplicitListsConfigurationUpdater {
    fun addLoadedSnippet(snippetData: ClassPathSnippetsLoadedData)

    fun getSnippetsReadyForConfigurationUpdate(): List<ClassPathSnippetsLoadedData>
}