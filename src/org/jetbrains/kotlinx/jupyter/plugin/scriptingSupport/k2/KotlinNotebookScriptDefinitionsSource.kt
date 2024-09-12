// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService

class KotlinNotebookScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = sequenceOf(
            JupyterCompilerService.getInstance(project)
                .scriptDefinitionsWrapper
                .compilationScriptDefinition
        )
}