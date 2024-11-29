// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.definition

import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.KotlinNotebookScriptDefinitionsWrapper
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class NotebookScriptDefinitionsWrapperFactoryK2 : KotlinNotebookScriptDefinitionsWrapper.Factory {
    override fun create(scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper {
        return K2NotebookScriptDefinitionsWrapper(scriptDefinition)
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