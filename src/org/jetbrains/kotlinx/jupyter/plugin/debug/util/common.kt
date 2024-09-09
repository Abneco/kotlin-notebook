// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util

import com.intellij.debugger.engine.JavaValue
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile


internal val debugFeaturesEnabled: Boolean
    get() = Registry.`is`("kotlin.notebook.debug.enabled", false)


/**
 * Based on current DebugSession of [BackedNotebookVirtualFile],
 * retrieves [JavaValue] by [variableName] in Kernel interpreter state or null.
 *
 */
fun BackedNotebookVirtualFile.retrieveSessionVariableValue(project: Project, variableName: String): JavaValue? {
    return KotlinNotebookSessionVariablesService.getForFile(project, this).getVariableValueByNameOrNull(variableName)
}

/**
 * Based on current DebugSession of [BackedNotebookVirtualFile],
 * retrieves all [JavaValue] in Kernel interpreter state
 */
suspend fun BackedNotebookVirtualFile.retrieveCurrentSessionVariables(project: Project): XValueChildrenList? {
    val variables = blockingContext {
        KotlinNotebookSessionVariablesService.getForFile(project, this).getXValueChildrenList()
    }
    return variables
}

fun KotlinNotebookDebugSession.createScreeningAttachment(): Attachment {
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