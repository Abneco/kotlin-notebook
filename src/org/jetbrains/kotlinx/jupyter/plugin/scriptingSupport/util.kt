// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import kotlin.script.experimental.api.ScriptCompilationConfiguration

val Project.baseScriptingCompilationConfiguration: ScriptCompilationConfiguration
    get() = JupyterCompilerService.getInstance(this).scriptDefinitionsWrapper.scriptDefinitionData.compilationConfiguration

val Project.scriptConfigurationsClassCache
    get() = (ScriptConfigurationManager.getInstance(this) as CompositeScriptConfigurationManager)
        .updater.classpathRoots

val Project.workSpaceSnapshot
    get() = WorkspaceModel.getInstance(this).currentSnapshot