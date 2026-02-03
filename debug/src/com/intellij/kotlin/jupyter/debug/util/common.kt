// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.debugger.engine.JavaValue
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookFileDebugSession
import com.intellij.kotlin.jupyter.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry


internal val debugFeaturesEnabled: Boolean
    get() = Registry.`is`("kotlin.notebook.debug.enabled", false)

internal val debugActionEnabled: Boolean
    get() = Registry.`is`("kotlin.notebook.debug.cell.action.enabled", false)

/**
 * Based on current DebugSession of [BackedNotebookVirtualFile],
 * retrieves [JavaValue] by [variableName] in Kernel interpreter state or null.
 *
 */
fun BackedNotebookVirtualFile.retrieveSessionVariableValue(project: Project, variableName: String): JavaValue? {
    return KotlinNotebookSessionVariablesService.getForFile(project, this).getVariableValueByNameOrNull(variableName)
}


internal fun KotlinNotebookFileDebugSession.createScreeningAttachment(): Attachment {
    val debugSession = this.debuggerSession
    val targetDebugPort = targetDebugPort
    val stackFrameProxy = currentStackFrameProxy

    return Attachment(
        "Debugger session for $virtualFile",
        buildString {
            appendLine("Debug session: $debugSession")
            appendLine("Debug connection: ${debugSession?.process?.connection}")
            appendLine("Debug port: $targetDebugPort")
            appendLine("Process isInitial: ${debugSession?.process?.isInInitialState}")
            appendLine("Process isDetached: ${debugSession?.process?.isDetached}")
            appendLine("StackFrame: $stackFrameProxy")
        }
    )
}