// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.fir

import com.intellij.kotlin.jupyter.k2.settings.KotlinNotebookK2ProjectOptionsProvider
import com.intellij.openapi.project.Project
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.compilerOptions

/**
 * Is responsible for configuring compiler plugins for the notebook script compilation configuration.
 * Options for this configuration are taken from [KotlinNotebookK2ProjectOptionsProvider.bundledCompilerPlugins].
 *
 * NB: right now only IDEA-bundled compiler plugins are supported.
 */
internal fun ScriptCompilationConfiguration.Builder.refineNotebookWithSelectedBundledCompilerPlugins(project: Project) {
    val selectedPlugins = KotlinNotebookK2ProjectOptionsProvider.getInstance(project).bundledCompilerPlugins

    appendPluginsToInclude(selectedPlugins)
    appendPluginsOptions(selectedPlugins)
}

/**
 * Amends [compilerOptions] specifying the option 'Xplugin' in the corresponding format
 * based on the list of plugins selected.
 */
private fun ScriptCompilationConfiguration.Builder.appendPluginsToInclude(plugins: List<NotebookAnalysisBundledCompilerPlugins>) {
    if (plugins.isEmpty()) return

    val pluginsSelection = buildString {
        append("-Xplugin=")
        for (plugin in plugins) {
            append(
                "${plugin.bundledJarLocation},"
            )
        }
    }

    compilerOptions.append(pluginsSelection)
}

/**
 * Amends [compilerOptions] with required compiler options for the selected plugins
 * to work properly in the notebook environment.
 */
private fun ScriptCompilationConfiguration.Builder.appendPluginsOptions(plugins: List<NotebookAnalysisBundledCompilerPlugins>) {
    if (plugins.isEmpty()) return

    for (plugin in plugins) {
        compilerOptions.append(plugin.pluginEnvironmentOptions)
    }
}