// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.notifications

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider.Listener

class KotlinNotebookPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        installProjectOptionsListener(project)
        showOutdatedKernelWarningIfNeeded(project)
    }

    private fun installProjectOptionsListener(project: Project) {
        project.service<KotlinNotebookProjectOptionsProvider>().apply {
            addListener(object : Listener {
                override fun onKernelVersionChanged() {
                    ignoreOutdatedKernelVersion = false
                    showOutdatedKernelWarningIfNeeded(project)
                }
            }, this)
        }
    }

    private fun showOutdatedKernelWarningIfNeeded(project: Project) {
        project.notebookNotifications.showOutdatedKernelWarningIfNeeded()
    }
}
