// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand


/**
 * Command waits until all notebook cells finish execution.
 * Example: `%waitExecutionFinishes 120000`
 */
class WaitExecutionFinishesCommand(text: String, line: Int) : AbstractKotlinJupyterCommand(text, line) {
    companion object {
        const val PREFIX: String = AbstractCommand.CMD_PREFIX + "waitExecutionFinishes"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val args = extractCommandArgument(PREFIX)
        if (args.isBlank()) {
            context.error("Timeout argument is missing. Expected timeout in milliseconds.", line)
            return
        }
        val timeoutMs = args.toLongOrNull() ?: run {
            context.error("Unexpected argument. Expected timeout in milliseconds, got: '$args'", line)
            return
        }
        waitExecutionFinishes(context, timeoutMs)
    }
}