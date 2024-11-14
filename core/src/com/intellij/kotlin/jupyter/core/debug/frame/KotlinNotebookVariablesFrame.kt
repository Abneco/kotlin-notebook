// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.frame

import com.intellij.debugger.engine.JavaDebuggerEvaluator
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSession
import com.intellij.kotlin.jupyter.core.debug.util.createScreeningAttachment
import com.intellij.kotlin.jupyter.core.debug.util.shouldShowNotebookVariables
import com.intellij.kotlin.jupyter.core.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.errorUnderDebug
import com.intellij.kotlin.jupyter.core.util.warnUnderDebug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame


/**
 * This [XStackFrame] represents current interpreter state
 * inside Kotlin Notebook.
 *
 * This means, all the top-level assignments executed by the user at the present moment.
 */
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

    // Since we focus on JVM, use Java implementations
    override fun getEvaluator(): XDebuggerEvaluator? {
        if (evaluator == null) {
            val debugProcess = debugSession.debuggerSession?.process ?: return null
            val frameProxy = debugSession.currentStackFrameProxy?.stackFrame as? JavaStackFrame
            evaluator = JavaDebuggerEvaluator(
                debugProcess,
                frameProxy
            )
        }
        return evaluator
    }

    override fun getSourcePosition(): XSourcePosition? = sourcePosition

    override fun computeChildren(node: XCompositeNode) {
        debugSession.ensureSilentSessionAlive()
        if (!project.shouldShowNotebookVariables) {
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
            LOG.warn("Session is not initialized")
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

        context.suspendContext.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
            try {
                val virtualMachine = context.suspendContext.virtualMachineProxy

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