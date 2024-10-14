// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.settings.JupyterExecutionSettings
import com.intellij.jupyter.core.jupyter.connections.execution.settings.JupyterExecutionSettingsImpl
import com.intellij.jupyter.core.jupyter.connections.execution.settings.JupyterExecutionSettingsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project

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
