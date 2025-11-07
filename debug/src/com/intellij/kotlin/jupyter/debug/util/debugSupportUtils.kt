// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.getSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.isKernelVersionEnoughForInstrumentation
import com.intellij.kotlin.jupyter.core.util.getKotlinNotebookVirtualFile
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookFileDebugSession
import com.intellij.kotlin.jupyter.debug.settings.KotlinNotebookDebugProjectOptionsProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession

internal val DataContext.debugSessionForFile: KotlinNotebookFileDebugSession?
    get() {
        val notebookFile = getKotlinNotebookVirtualFile()
        if (notebookFile == null) {
            return null
        }
        val project = CommonDataKeys.PROJECT.getData(this)!!

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

internal val Project.notebookDebugFeaturesSupported: Boolean
    get() = debugFeaturesEnabled && isKernelVersionEnoughForInstrumentation

internal val Project.shouldShowNotebookVariables: Boolean
    get() = KotlinNotebookDebugProjectOptionsProvider.getInstance(this).shouldShowNotebookVariables
            && debugFeaturesEnabled

internal val Project.shouldFocusOnVariablesToolWindow: Boolean
    get() = shouldShowNotebookVariables && KotlinNotebookDebugProjectOptionsProvider.getInstance(this).shouldFocusOnVariables

internal val Project.hasNotebookDebugSession: Boolean
    get() = debugFeaturesEnabled && KotlinNotebookDebugSessionManager.getInstance(this).hasAnyAttachedProcess

internal val KotlinNotebookSessionRunMode.debugFeaturesSupported: Boolean
    get() = when (this) {
        KotlinNotebookSessionRunMode.SEPARATE_PROCESS -> true
        KotlinNotebookSessionRunMode.IDE_PROCESS,
        KotlinNotebookSessionRunMode.ATTACHED_PROCESS -> false
    }

/**
 * Checks if debug session could be instantiated for the notebook settings
 */
internal fun BackedNotebookVirtualFile.debugFeaturesSupported(project: Project): Boolean {
    if (!project.notebookDebugFeaturesSupported) return false
    return getSessionRunMode(project).debugFeaturesSupported
}