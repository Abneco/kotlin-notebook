// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.settings.JupyterExecutionSettings
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.settings.JupyterExecutionSettingsImpl
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.settings.JupyterExecutionSettingsProvider

class KotlinJupyterExecutionSettingsProvider : JupyterExecutionSettingsProvider {
    override fun getSettings(project: Project, notebookFile: BackedNotebookVirtualFile): JupyterExecutionSettings? {
        return if (notebookFile.isKotlinNotebook) {
            val settings = KotlinNotebookApplicationOptions.get()
            JupyterExecutionSettingsImpl(
                stopExecutionsOnFailure = settings.shouldStopExecutionOnFailure
            )
        } else {
            null
        }
    }
}
