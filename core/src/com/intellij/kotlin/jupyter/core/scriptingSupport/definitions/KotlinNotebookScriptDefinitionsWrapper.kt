// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.definitions

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.ide.handlers.createPluginModeAwareInstance
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

/**
 * Class representing a wrapper around script definitions for Kotlin notebook scripts,
 * being mode-aware for both Kotlin plugin modes.
 *
 * Note that [scriptDefinitionData] is basically data holder used in some IDEA logic, while
 * [compilationScriptDefinition] is an integral compiler representation for the Script definition itself.
 */
internal sealed class KotlinNotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinPluginModeAwareHandler {
    val scriptDefinitionData: ScriptDefinition by lazy {
        scriptDefinition
    }

    protected fun isNotebookInjectedScript(script: SourceCode): Boolean {
        val virtualFile = (script as? VirtualFileScriptSource)?.virtualFile ?: return false

        return when (virtualFile) {
            is VirtualFileWindow -> virtualFile.delegate.toKotlinNotebookBackedFile() != null
            else -> virtualFile.isKotlinNotebook
        }
    }

    abstract val compilationScriptDefinition: org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
}


internal class K1NotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinNotebookScriptDefinitionsWrapper(scriptDefinition) {
    override val compilationScriptDefinition by lazy {
        object : org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromNewDefinition(
            defaultJvmScriptingHostConfiguration,
            scriptDefinitionData
        ) {
            init {
                order = Int.MIN_VALUE
            }
        }
    }
}

internal class K2NotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinNotebookScriptDefinitionsWrapper(scriptDefinition) {
    override val compilationScriptDefinition by lazy {
        object : org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            scriptDefinition.compilationConfiguration,
            scriptDefinition.evaluationConfiguration
        ) {
            init {
              order = Int.MIN_VALUE
            }

            override fun isScript(script: SourceCode): Boolean {
                return super.isScript(script) && isNotebookInjectedScript(script)
            }
        }
    }
}


internal fun createNotebookScriptDefinitionsWrapper(scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper {
    return createPluginModeAwareInstance(
        scriptDefinition,
        ::K1NotebookScriptDefinitionsWrapper,
        ::K2NotebookScriptDefinitionsWrapper,
    )
}