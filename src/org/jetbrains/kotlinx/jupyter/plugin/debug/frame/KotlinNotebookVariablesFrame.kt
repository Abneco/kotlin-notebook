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
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.NotebookSessionVariablesService
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
        val debugProcess = debugSession.debuggerSession?.process
        if (node.isObsolete || debugProcess == null) {
            node.setErrorMessage("Variables are not available, no connection is established")
            return
        }
        if (!debugProcess.isAttached) {
            if (debugProcess.isInInitialState) {
                node.setErrorMessage("Variables will be rebuild after connection is established")
                return
            }
            node.setErrorMessage("Variables are not available")
            debugSession.disposeCurrentSession()
            LOG.warn("Session is not initialised, disposing")
            return
        }

        val variablesService = NotebookSessionVariablesService.getForFile(project, debugSession.virtualFile)
        val context = debugSession.evaluationContext

        if (context == null) {
            node.setErrorMessage("Computations are not available without suspended context")
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