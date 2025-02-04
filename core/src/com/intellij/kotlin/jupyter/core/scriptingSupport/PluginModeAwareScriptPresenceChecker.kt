// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.PluginModeAwareScriptPresenceChecker.Companion.create
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project


/**
 * A functional interface that acts as a checker to determine if a script is present in Workspace Model,
 * depending on the mode of the Kotlin plugin (K1 or K2).
 *
 * This is crucial to now since only after a script was added to the Model, it's now in indexes.
 *
 * [create] calls a [Factory] service for each of K1/K2 modes.
 */
fun interface PluginModeAwareScriptPresenceChecker : KotlinPluginModeAwareHandler {
    fun checkPresentInCache(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean

    interface Factory {
        fun create(project: Project): PluginModeAwareScriptPresenceChecker
    }

    companion object {
        fun create(project: Project): PluginModeAwareScriptPresenceChecker {
            return project.service<Factory>().create(project)
        }
    }
}