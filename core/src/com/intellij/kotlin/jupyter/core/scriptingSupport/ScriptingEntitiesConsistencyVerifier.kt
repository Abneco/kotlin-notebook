// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier.Companion.create
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.script.experimental.api.ScriptCompilationConfiguration


/**
 * An interface that acts as a checker to determine if a script or its configuration is present in Workspace Model / cache,
 * depending on the mode of the Kotlin plugin (K1 or K2).
 *
 * This is crucial to now since only after a script was added to the Model, it's now in indexes.
 *
 * [create] calls a [Factory] service for each of K1/K2 modes.
 */
interface ScriptingEntitiesConsistencyVerifier : KotlinPluginModeAwareHandler {
    /**
     * Check for presence of classpath part in artifacts cache
     */
    fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean

    /**
     * Checks for the presence of a particular refined configuration
     */
    fun isScriptFileConfigurationConsistentWithModel(virtualFile: BackedNotebookVirtualFile, compilationConfiguration: ScriptCompilationConfiguration): Boolean

    interface Factory {
        fun create(project: Project): ScriptingEntitiesConsistencyVerifier
    }

    companion object {
        fun create(project: Project): ScriptingEntitiesConsistencyVerifier {
            return project.service<Factory>().create(project)
        }
    }
}