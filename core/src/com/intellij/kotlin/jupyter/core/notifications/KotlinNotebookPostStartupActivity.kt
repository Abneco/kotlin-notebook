// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.notifications

import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider.Listener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class KotlinNotebookPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        installProjectOptionsListener(project)
        showOutdatedKernelWarningIfNeeded(project)
    }

    private fun installProjectOptionsListener(project: Project) {
        val projectOptions = KotlinNotebookProjectOptionsProvider.getInstance(project)
        projectOptions.addListener(object : Listener {
            override fun onKernelVersionChanged() {
                projectOptions.ignoreOutdatedKernelVersion = false
                showOutdatedKernelWarningIfNeeded(project)
            }

            override fun onExtraCompilerArgumentsChanged() {
                resetScriptDefinitionAndShowKernelRestartNeededWarning(project)
            }
        }, projectOptions)


        KotlinNotebookApplicationOptions.get()
            .addListener(object : KotlinNotebookApplicationOptionsProvider.Listener {
                override fun onReplCompilerModeChanged() {
                    resetScriptDefinitionAndShowKernelRestartNeededWarning(project)
                }
            }, projectOptions)
    }

    private fun showOutdatedKernelWarningIfNeeded(project: Project) {
        project.notebookNotifications.showOutdatedKernelWarningIfNeeded()
    }

    private fun resetScriptDefinitionAndShowKernelRestartNeededWarning(project: Project) {
        JupyterCompilerService.getInstance(project).resetScriptDefinition()
        project.notebookNotifications.showKernelRestartNeeded()
    }
}
