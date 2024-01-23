// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.frame

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSession
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.NotebookSessionVariablesService

class KotlinNotebookVariablesFrame(
    private val project: Project,
    private val sourcePosition: XSourcePosition?,
    private val debugSession: KotlinNotebookDebugSession
) : XStackFrame() {
    companion object {
        private val LOG = thisLogger()
        private val STACK_FRAME_EQUALITY_OBJECT = Any()

        data class VariablesStateAccessorData(
            val nextEntryFieldAccessor: Field,
            var mapEntryReference: ObjectReference,
            val hashMapNodeClassType: ClassType
        )
    }
    override fun getEqualityObject(): Any? = STACK_FRAME_EQUALITY_OBJECT

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

        debugProcess.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
            try {
                val virtualMachine = debugProcess.virtualMachineProxy
                //val notebookClass = virtualMachine.classesByNameProvider.get("org.jetbrains.kotlinx.jupyter.NotebookImpl").firstOrNull() ?: return@invoke
                // todo: make it field first
                ////val getter = notebookClass.methodsByName("getVariablesState").firstOrNull() ?: return@invoke

                node.addChildren(
                    variablesService.representVariablesStateAsXContainer(virtualMachine),
                    true
                )
            } catch (ex: Exception) {
                LOG.error("Error during variables state computation: ", ex)
            } finally {
                if (debugSession.debuggerSession?.isPaused == true) {
                    debugProcess.managerThread.schedule(PrioritizedTask.Priority.HIGH) {
                        runInEdt {
                            for (child in (node as XStackFrameNode).loadedChildren) {
                                val container = child.valueContainer as? JavaValue ?: continue

                                if (container.descriptor.isExpandable) {
                                    child.startComputingChildren()
                                }
                            }


                            debugProcess.managerThread.schedule(PrioritizedTask.Priority.LOWEST) {
                                runInEdt {
                                    debugSession.debuggerSession?.xDebugSession?.resume()
                                }
                            }
                        }
                    }
                }
            }
        }

        super.computeChildren(node)
    }

}