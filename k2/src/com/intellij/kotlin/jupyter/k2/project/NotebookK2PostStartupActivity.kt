// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project

import com.intellij.kotlin.jupyter.core.settings.actions.promptSessionShutdownIfNeeded
import com.intellij.kotlin.jupyter.core.util.getCurrentEditorOrNull
import com.intellij.kotlin.jupyter.k2.settings.KotlinNotebookK2ProjectOptionsProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerPluginsScriptConfigurationListener

class NotebookK2PostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        installProjectOptionsListener(project)
    }

    private fun installProjectOptionsListener(project: Project) {
        KotlinNotebookK2ProjectOptionsProvider.getInstance(project).apply {
            addListener(object : KotlinNotebookK2ProjectOptionsProvider.Listener {
                override fun onCompilerPluginsChanged() {
                    val editor = project.getCurrentEditorOrNull() ?: return
                    promptSessionShutdownIfNeeded(NotebookK2PostStartupActivity::class, editor) {
                        project.notifyCompilerPluginsSettingsChanged()
                    }
                }
            }, this)
        }
    }

    private fun Project.notifyCompilerPluginsSettingsChanged() {
        messageBus.syncPublisher(KotlinCompilerPluginsScriptConfigurationListener.TOPIC).scriptConfigurationsChanged()
    }
}