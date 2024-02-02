// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.actions.evaluate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.getNotebookXSessionOrNull
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.isEvaluationPossible
import org.jetbrains.kotlinx.jupyter.plugin.util.getKotlinNotebookVirtualFile

class NotebookSilentEvaluateActionHandler : XDebuggerEvaluateActionHandler() {
    override fun isEnabled(project: Project, event: AnActionEvent?): Boolean {
        val notebook = event?.dataContext?.getKotlinNotebookVirtualFile() ?: return false
        val sessionManager = KotlinNotebookDebugSessionManager.getForFile(project, notebook)
        val xSession = sessionManager.currentXSession ?: return false
        return isEnabled(xSession, event.dataContext)
    }

    override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
        return dataContext.isEvaluationPossible
    }

    override fun perform(project: Project, event: AnActionEvent) {
        val xSession = event.dataContext.getNotebookXSessionOrNull() ?: return

        val context = event.dataContext
        super.perform(xSession, context)
    }

    private fun showSilentEvaluateDialog(session: XDebugSession, dataContext: DataContext) {
        val editorsProvider = session.getDebugProcess().getEditorsProvider()
        val stackFrame = session.getCurrentStackFrame()
        val evaluator = session.getDebugProcess().getEvaluator()
        val virtualFile = dataContext.getKotlinNotebookVirtualFile()?.file

        if (evaluator == null || virtualFile == null) {
            return
        }

        val expr2 = XDebuggerUtil.getInstance()
            .createExpression("this.variablesState", null, null, EvaluationMode.EXPRESSION)


        //invokeOnEdt {
        //    showDialog(
        //        session,
        //        virtualFile,
        //        editorsProvider,
        //        stackFrame,
        //        evaluator,
        //        expr2
        //    )
        //}
    }
}