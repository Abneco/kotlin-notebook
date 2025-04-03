// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
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
    val scripts: List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>>,
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
        ScratchUtil.isScratch(it.key) || it.value.valueOrNull() == null
    }.mapValues { entry ->
        val (scriptFile, configuration) = entry
        scriptFile to configuration.valueOrThrow()
    }.entries.groupBy(
        keySelector = { (it.key as VirtualFileWindow).delegate },
        valueTransform = { it.value }
    ).mapValues {
        KotlinNotebookScriptsModuleConfigurationInfo(
            it.key,
            it.value,
            sdk
        )
    }
}

internal fun Map<VirtualFile, ScriptConfigurationWithSdk>.getConfigurationsForNotebook(notebookFile: VirtualFile): Collection<ScriptCompilationConfigurationWrapper>? {
    return this.filter { (vFile, configurationWrapper) ->
        (vFile as? VirtualFileWindow)?.delegate == notebookFile && configurationWrapper.scriptConfiguration.valueOrNull() != null
    }.map { it.value.scriptConfiguration.valueOrThrow() }
}