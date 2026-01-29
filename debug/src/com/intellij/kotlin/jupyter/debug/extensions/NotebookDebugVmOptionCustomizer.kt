// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.extensions

import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions.KernelDebugOptionsCustomizer
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.sessionRunMode
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.util.debugFeaturesSupported
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * This class is responsible for setting up the debug port for the kernel process,
 * running only in [KotlinNotebookSessionRunMode.SEPARATE_PROCESS]
 */
class NotebookDebugVmOptionCustomizer : KernelDebugOptionsCustomizer {
    override fun getDebugPort(project: Project, notebookPath: Path): Int? {
        val debugService = KotlinNotebookDebugSessionManager.getInstance(project).getByPath(notebookPath)
        if (debugService == null) {
            return null
        }

        val isEnabled = debugService.virtualFile.notebook.sessionRunMode.debugFeaturesSupported
        if (!isEnabled) return null

        return debugService.provideFreshDebugPortOrNull()
    }
}