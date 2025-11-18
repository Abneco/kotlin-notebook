// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

/**
 * K2-based plugin abstraction for describing configuration data for the script.
 * This class is heavily used in [org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource]
 *
 * @see [NotebookScriptConfigurationsManager]
 */
class KotlinNotebookScriptModel(
    val virtualFile: VirtualFile,
    val refinedConfiguration: ScriptCompilationConfigurationWrapper
)
