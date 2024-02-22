// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util

import com.intellij.debugger.engine.JavaValue
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.NotebookSessionVariablesService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

fun BackedNotebookVirtualFile.retrieveVariableValue(project: Project, variableName: String): JavaValue? {
    return NotebookSessionVariablesService.getForFile(project, this).getVariableValueByNameOrNull(variableName)
}

suspend fun BackedNotebookVirtualFile.retrieveCurrentVariables(project: Project): XValueChildrenList? {
    val variables = blockingContext {
        NotebookSessionVariablesService.getForFile(project, this).getXValueChildrenList()
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