// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.valueOrThrow

/**
 * K2-based plugin abstraction for describing configuration data for the script.
 * This class is heavily used in [org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesSource]
 *
 * @see [NotebookScriptDependenciesSource]
 */
class KotlinNotebookScriptModel(
    virtualFile: VirtualFile,
    val injectedKtFile: KtFile,
    val refinedConfigurationResult: ScriptCompilationConfigurationWrapper
) : BaseScriptModel(virtualFile)


data class KotlinNotebookScriptsModuleConfigurationInfo(
    val notebookFile: VirtualFile,
    val scripts: List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>>,
    val sdkInfo: Sdk?
)

/**
 * Transforms all passed [ScriptDependenciesData] to a map separated by [com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile]
 * Each [KotlinNotebookScriptsModuleConfigurationInfo] contains all necessary information for each cell.
 */
internal fun ScriptDependenciesData.toConfigurationInfoPerNotebook(): Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo> {
    val sdk = sdks.values.firstOrNull()

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