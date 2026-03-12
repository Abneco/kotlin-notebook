// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.settings.NotebookProjectJdkOption
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlinx.jupyter.repl.result.SerializedCompiledScript
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.util.PropertiesCollection

val Project.baseScriptingCompilationConfiguration: ScriptCompilationConfiguration
    get() = JupyterCompilerService.getInstance(this).scriptDefinitionsWrapper.scriptDefinitionData.compilationConfiguration

val SerializedCompiledScript.classFQN: String
    get() = fileName
        .removeSuffix(".class")
        .replace('$', '.')
        .replace('/', '.')

val IdeScriptCompilationConfigurationKeys.serializationPluginEnabled: PropertiesCollection.Key<Boolean>
        by PropertiesCollection.key(false)

private fun Sdk.canBeUsedForScript(): Boolean {
    if (sdkType !is JavaSdkType) return false
    val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
    return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
}

fun getSelectedSdkOrAnyAcceptable(project: Project): Sdk? {
    val registeredJdks = ProjectJdkTable.getInstance().allJdks.toSet().ifEmpty {
        return null
    }
    return NotebookProjectJdkOption.suggestJdks(project).firstOrNull {
        it.canBeUsedForScript() && it in registeredJdks
    }
}

fun ScriptCompilationConfigurationWrapper.with(body: ScriptCompilationConfiguration.Builder.() -> Unit): ScriptCompilationConfigurationWrapper {
    return ScriptCompilationConfigurationWrapper(this.script, configuration.with(body))
}