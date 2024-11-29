// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService

/**
 * Kotlin Notebook main class to provide the plugin main script definition.
 */
class KotlinNotebookScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<org.jetbrains.kotlin.scripting.definitions.ScriptDefinition>
        get() = sequenceOf(
          JupyterCompilerService.getInstance(project)
                .scriptDefinitionsWrapper
                .compilationScriptDefinition
        )
}