// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

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