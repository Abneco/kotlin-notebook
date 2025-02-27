// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.definitions

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.KotlinNotebookScriptDefinitionsWrapper.Companion.create
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.scripting.definitions.StandardScriptDefinition.fileExtension
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.host.ScriptDefinition

/**
 * Class representing a wrapper around script definitions for Kotlin notebook scripts,
 * being mode-aware for both Kotlin plugin modes.
 *
 * Note that [scriptDefinitionData] is basically data holder used in some IDEA logic, while
 * [compilationScriptDefinition] is an integral compiler representation for the Script definition itself.
 *
 * [create] calls a [Factory] service for each of K1/K2 modes.
 */
abstract class KotlinNotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinPluginModeAwareHandler {
    fun interface Factory {
        fun create(scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper
    }

    val scriptDefinitionData: ScriptDefinition by lazy {
        scriptDefinition
    }

    protected val fileExtension: String by lazy {
        scriptDefinition.compilationConfiguration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter.kts"
    }

    protected fun isNotebookInjectedScript(script: SourceCode): Boolean {
        val virtualFile = (script as? VirtualFileScriptSource)?.virtualFile ?: return false

        return when (virtualFile) {
            is VirtualFileWindow -> virtualFile.delegate.toKotlinNotebookBackedFile() != null
            // Special case for tmp files as we don't want to create a notebook ViewProvider
            is LightVirtualFile -> virtualFile.name.endsWith(fileExtension)
            else -> virtualFile.isKotlinNotebook
        }
    }

    abstract val compilationScriptDefinition: org.jetbrains.kotlin.scripting.definitions.ScriptDefinition

    companion object {
        fun create(project: Project, scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper {
            return project.service<Factory>().create(scriptDefinition)
        }
    }
}
