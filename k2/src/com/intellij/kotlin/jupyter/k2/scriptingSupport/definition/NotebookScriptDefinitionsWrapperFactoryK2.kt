// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.definition

import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.KotlinNotebookScriptDefinitionsWrapper
import com.intellij.kotlin.jupyter.k2.scriptingSupport.fir.refineNotebookWithSelectedBundledCompilerPlugins
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class NotebookScriptDefinitionsWrapperFactoryK2(val project: Project) : KotlinNotebookScriptDefinitionsWrapper.Factory {
    override fun create(scriptDefinition: ScriptDefinition): KotlinNotebookScriptDefinitionsWrapper {
        return K2NotebookScriptDefinitionsWrapper(scriptDefinition, project)
    }
}

internal class K2NotebookScriptDefinitionsWrapper(
    scriptDefinition: ScriptDefinition,
    project: Project
) : KotlinNotebookScriptDefinitionsWrapper(scriptDefinition) {
    override val compilationScriptDefinition by lazy {
        var compilationConfiguration = scriptDefinition.compilationConfiguration.with {
            refineNotebookWithSelectedBundledCompilerPlugins(project)
        }
        // notify definition update, as lazy value computed
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()

        object : org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            scriptDefinition.evaluationConfiguration
        ) {
            init {
                order = Int.MIN_VALUE
            }

            override fun isScript(script: SourceCode): Boolean {
                return when {
                    super.isScript(script) -> {
                        isNotebookInjectedScript(script)
                    }
                    // it might be a compiled artifact from a notebook's ScriptModule
                    script is VirtualFileScriptSource -> {
                        script.name?.endsWith(compiledFileSuffix) == true
                    }
                    else -> false
                }
            }
        }
    }
}