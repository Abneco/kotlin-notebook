// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.valueOrThrow

/**
 * K2-based plugin abstraction for describing configuration data for the script.
 * This class is heavily used in [org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource]
 *
 * @see [NotebookScriptConfigurationsManager]
 */
class KotlinNotebookScriptModel(
    val virtualFile: VirtualFile,
    val refinedConfigurationResult: ScriptCompilationConfigurationWrapper
)

data class KotlinNotebookScriptsModuleConfigurationInfo(
    val notebookFile: VirtualFile,
    val configuration: ScriptCompilationConfigurationWrapper,
    val sdkInfo: Sdk?
)

/**
 * Transforms all passed [ScriptConfigurationWithSdk] to a map separated by [com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile]
 * Each [KotlinNotebookScriptsModuleConfigurationInfo] contains all necessary information for each cell.
 */
internal fun Map<VirtualFile, ScriptConfigurationWithSdk>.toConfigurationInfoPerNotebook(): Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo> {
    val sdk = values.firstOrNull()?.sdk
    val configurations = this.mapValues { it.value.scriptConfiguration }

    return configurations.filterNot {
        it.value.valueOrNull() == null
    }.mapValues { entry ->
        val (topLevelFile, configuration) = entry
        KotlinNotebookScriptsModuleConfigurationInfo(
            topLevelFile,
            configuration.valueOrThrow(),
            sdk
        )
    }
}

internal fun Map<VirtualFile, ScriptConfigurationWithSdk>.getConfigurationForNotebook(notebookFile: VirtualFile): ScriptCompilationConfigurationWrapper? {
    if (this.isEmpty()) return null

    return this[notebookFile]?.scriptConfiguration?.valueOrNull()
}