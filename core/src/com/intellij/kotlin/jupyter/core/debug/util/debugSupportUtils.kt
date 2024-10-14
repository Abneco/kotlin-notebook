// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.util

import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSession
import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.util.getKotlinNotebookVirtualFile
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.kotlin.idea.refactoring.project

internal val DataContext.debugSessionForFile: KotlinNotebookDebugSession?
    get() {
        val notebookFile = getKotlinNotebookVirtualFile()
        if (notebookFile == null) {
            return null
        }

        return KotlinNotebookDebugSessionManager.getForFile(project, notebookFile)
    }

internal val DataContext.isSilentSessionAvailable: Boolean
    get() = debugSessionForFile?.isLiveSession == true

internal val DataContext.isEvaluationPossible: Boolean
    get() = debugSessionForFile?.evaluationContext != null

internal fun DataContext.getNotebookXSessionOrNull(): XDebugSession? {
    val debugSession = debugSessionForFile

    return debugSession?.currentXSession
}

internal val Project.shouldShowNotebookVariables: Boolean
    get() = KotlinNotebookProjectOptionsProvider.getInstance(this).shouldShowNotebookVariables
            && debugFeaturesEnabled