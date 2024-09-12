// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.definitions

import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.KotlinPluginModeAwareHandler
import org.jetbrains.kotlinx.jupyter.plugin.ide.handlers.createPluginModeAwareInstance
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

    abstract val compilationScriptDefinition: org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
}


internal class K1NotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinNotebookScriptDefinitionsWrapper(scriptDefinition) {
    override val compilationScriptDefinition by lazy {
        org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromNewDefinition(
            defaultJvmScriptingHostConfiguration,
            scriptDefinitionData
        )
    }
}

internal class K2NotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition
) : KotlinNotebookScriptDefinitionsWrapper(scriptDefinition) {
    override val compilationScriptDefinition by lazy {
        org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            scriptDefinition.compilationConfiguration,
            scriptDefinition.evaluationConfiguration
        )
    }
}


internal fun createNotebookScriptDefinitionsWrapper(scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper {
    return createPluginModeAwareInstance(
        scriptDefinition,
        ::K1NotebookScriptDefinitionsWrapper,
        ::K2NotebookScriptDefinitionsWrapper,
    )
}