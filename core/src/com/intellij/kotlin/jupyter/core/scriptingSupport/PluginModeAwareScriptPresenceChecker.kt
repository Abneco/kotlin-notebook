// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project


data class ScriptCheckerConfiguration(
    val project: Project,
    val notebookFile: BackedNotebookVirtualFile
)

/**
 * A functional interface that acts as a checker to determine if a script is present in Workspace Model,
 * depending on the mode of the Kotlin plugin (K1 or K2).
 *
 * This is crucial to now since only after a script was added to the Model, it's now in indexes.
 */
fun interface PluginModeAwareScriptPresenceChecker : KotlinPluginModeAwareHandler {
    fun checkPresentInCache(lastCompiledScriptPath: String): Boolean

    interface Factory {
        fun create(configuration: ScriptCheckerConfiguration): PluginModeAwareScriptPresenceChecker
    }

    companion object {
        private val EP: ExtensionPointName<Factory> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.scriptPresenceCheckerFactory")

        fun create(project: Project, virtualFile: BackedNotebookVirtualFile): PluginModeAwareScriptPresenceChecker {
            val configuration = ScriptCheckerConfiguration(project, virtualFile)
            return EP.extensionList.first().create(configuration)
        }
    }
}