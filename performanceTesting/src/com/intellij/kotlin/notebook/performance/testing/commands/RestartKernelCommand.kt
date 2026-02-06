// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.notebook.performance.testing.commands

import com.intellij.jupyter.core.jupyter.connections.action.JupyterKernelRestartSupport
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand


/**
 * Command restarts the kernel for the current notebook.
 * Example: %restartKernel
 */
class RestartKernelCommand(text: String, line: Int) : AbstractKotlinJupyterCommand(text, line) {
    companion object {
        const val PREFIX: String = AbstractCommand.CMD_PREFIX + "restartKernel"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val backedFile = getBackedFile(context) ?: return
        JupyterKernelRestartSupport.restartKernel(context.project, backedFile)
    }
}
