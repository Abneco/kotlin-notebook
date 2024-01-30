// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.kotlin.idea.refactoring.project
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.getKotlinNotebookVirtualFile

object NotebookDebugSessionSupportUtils {
    val DataContext.debugSessionForFile: KotlinNotebookDebugSession?
        get() {
            val notebookFile = getKotlinNotebookVirtualFile()
            if (notebookFile == null) {
                return null
            }

            return KotlinNotebookDebugSessionManager.getForFile(project, notebookFile)
        }

    val DataContext.isSilentSessionAvailable: Boolean
        get() = debugSessionForFile?.isLiveSession == true

    val DataContext.isEvaluationPossible: Boolean
        get() = debugSessionForFile?.evaluationContext != null

    fun DataContext.getNotebookXSessionOrNull(): XDebugSession? {
        val debugSession = debugSessionForFile

        return debugSession?.currentXSession
    }

    val Project.isShouldShowNotebookVariables: Boolean
        get() = KotlinNotebookProjectOptionsProvider.getInstance(this).shouldShowNotebookVariables
}