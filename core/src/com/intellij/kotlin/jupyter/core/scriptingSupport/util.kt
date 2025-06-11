// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.util.PropertiesCollection

internal val Project.baseScriptingCompilationConfiguration: ScriptCompilationConfiguration
    get() = JupyterCompilerService.getInstance(this).scriptDefinitionsWrapper.scriptDefinitionData.compilationConfiguration

val Project.workSpaceSnapshot: ImmutableEntityStorage
    get() = WorkspaceModel.getInstance(this).currentSnapshot

val SerializedCompiledScript.classFQN: String
    get() = fileName.removeSuffix(".class").replace('$', '.')

val IdeScriptCompilationConfigurationKeys.serializationPluginEnabled: PropertiesCollection.Key<Boolean>
        by PropertiesCollection.key(false)