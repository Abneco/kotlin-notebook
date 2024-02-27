// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.frame

import com.intellij.debugger.engine.JavaDebuggerEvaluator
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.NotebookDebugSessionSupportUtils.isShouldShowNotebookVariables
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.createScreeningAttachment
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.KotlinNotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.util.errorUnderDebug
import org.jetbrains.kotlinx.jupyter.plugin.util.warnUnderDebug

class KotlinNotebookVariablesFrame(
    private val project: Project,
    private val sourcePosition: XSourcePosition?,
    private val debugSession: KotlinNotebookDebugSession
) : XStackFrame() {
    companion object {
        private val LOG = thisLogger()
        private val STACK_FRAME_EQUALITY_OBJECT = Any()
    }
    private var evaluator: XDebuggerEvaluator? = null

    override fun getEqualityObject(): Any? = STACK_FRAME_EQUALITY_OBJECT

    override fun getEvaluator(): XDebuggerEvaluator? {
        if (evaluator == null) {
            evaluator = JavaDebuggerEvaluator(debugSession.debuggerSession?.process, debugSession.currentStackFrameProxy?.stackFrame as? JavaStackFrame)
        }
        return evaluator
    }

    override fun getSourcePosition(): XSourcePosition? = sourcePosition

    override fun computeChildren(node: XCompositeNode) {
        debugSession.ensureSilentSessionAlive()
        if (!project.isShouldShowNotebookVariables) {
            super.computeChildren(node)
            return
        }

        val debugProcess = debugSession.debuggerSession?.process
        if (node.isObsolete || debugProcess == null) {
            node.setErrorMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.empty.no.connection.message")
            )
            return
        }
        if (!debugProcess.isAttached) {
            if (debugProcess.isInInitialState) {
                node.setErrorMessage(
                    KotlinNotebookBundle.message("kotlin.jupyter.debug.node.rebuild.message")
                )
                return
            }
            node.setErrorMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.empty.not.attached.message")
            )
            LOG.warn("Session is not initialised")
            return
        }

        val variablesService = KotlinNotebookSessionVariablesService.getForFile(project, debugSession.virtualFile)
        val context = debugSession.evaluationContext

        if (context == null) {
            node.setErrorMessage(
                KotlinNotebookBundle.message("kotlin.jupyter.debug.node.no.context.message")
            )
            LOG.errorUnderDebug(
                "Suspended context is null",
                debugSession.createScreeningAttachment()
            )
            return
        }

        debugProcess.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
            try {
                val virtualMachine = debugProcess.virtualMachineProxy

                node.addChildren(
                    variablesService.representVariablesStateAsXContainer(virtualMachine, context),
                    true
                )
            } catch (ex: Exception) {
                LOG.error("Error during variables state computation: ", ex)
            } finally {
                LOG.warnUnderDebug("Is paused: ${debugSession.debuggerSession?.isPaused == true}")
            }
        }

        super.computeChildren(node)
    }

}