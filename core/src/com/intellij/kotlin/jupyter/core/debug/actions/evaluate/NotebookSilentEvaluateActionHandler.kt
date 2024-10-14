// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.actions.evaluate

import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.debug.util.getNotebookXSessionOrNull
import com.intellij.kotlin.jupyter.core.debug.util.isEvaluationPossible
import com.intellij.kotlin.jupyter.core.util.getKotlinNotebookVirtualFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler

/***
 * This class handles the behavior of 'Evaluate Expression' action in Kotlin Notebook.
 * Since we have [SuspendedContext] provided, it's possible to reuse logic of evaluations from
 * the superclass.
 */
class NotebookSilentEvaluateActionHandler : XDebuggerEvaluateActionHandler() {
    override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
        val notebook = event.dataContext.getKotlinNotebookVirtualFile() ?: return false
        val sessionManager = KotlinNotebookDebugSessionManager.getForFile(project, notebook)
        val xSession = sessionManager.currentXSession ?: return false
        return isEnabled(xSession, event.dataContext)
    }

    override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
        return dataContext.isEvaluationPossible
    }

    // Currently, code insight is not provided. Needs investigation in Scripting
    override fun perform(project: Project, event: AnActionEvent) {
        val xSession = event.dataContext.getNotebookXSessionOrNull() ?: return

        val context = event.dataContext
        super.perform(xSession, context)
    }
}