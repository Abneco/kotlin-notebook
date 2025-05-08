// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.extensions

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.settings.compilerPluginsEnabled
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookConfigurable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row

/**
 * Provides UI settings components for compiler plugins selection
 * inside [KotlinNotebookConfigurable] based on current Kotlin plugin mode.
 *
 * NB: for K1, no customization is applicable.
 */
interface NotebookCompilerPluginSettingsBuilder : KotlinPluginModeAwareHandler {
    /**
     * Main entry point to create UI components for compiler plugins settings.
     * This method would be called as a part of [KotlinNotebookConfigurable.createPanel], JVM options subpanel.
     */
    fun buildCompilerPluginsOptionsSelector(project: Project, settingsPanel: Panel): Row? = null

    companion object {
        private val EP: ExtensionPointName<NotebookCompilerPluginSettingsBuilder> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.settings.compilerPluginsBuilder")

        fun buildCompilerPluginsOptionsSelector(project: Project, settingsPanel: Panel): Row? {
            if (!compilerPluginsEnabled) return null

            val extensions = EP.extensionList
            return extensions.firstNotNullOfOrNull { builder ->
                builder.buildCompilerPluginsOptionsSelector(project, settingsPanel)
            }
        }
    }
}