// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.frame

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.ui.tree.nodes.XStackFrameNode
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableStateDescriptor
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KJupyterNotebookDebugSession

class KotlinNotebookVariablesFrame(
    private val project: Project,
    private val sourcePosition: XSourcePosition?,
    private val debugSession: KJupyterNotebookDebugSession
) : XStackFrame() {
    companion object {
        private val LOG = thisLogger()
        private val STACK_FRAME_EQUALITY_OBJECT = Any()

        private data class VariablesStateAccessorData(
            val nextEntryFieldAccessor: Field,
            var mapEntryReference: ObjectReference,
            val hashMapNodeClassType: ClassType
            )
    }
    override fun getEqualityObject(): Any? = STACK_FRAME_EQUALITY_OBJECT

    override fun getSourcePosition(): XSourcePosition? = sourcePosition

    override fun computeChildren(node: XCompositeNode) {
        val debugProcess = debugSession.debuggerSession?.process
        if (node.isObsolete || debugProcess == null) {
            node.setErrorMessage("Variables are not available")
            return
        }
        if (!debugProcess.isAttached || debugSession.debuggerSession?.isStopped == true) {
            node.setErrorMessage("Variables are not available")
            debugSession.disposeCurrentSession()
            LOG.warn("Session is not initialised, disposing")
            return
        }


        debugProcess.managerThread.invoke(PrioritizedTask.Priority.HIGH) {
            try {
                val virtualMachine = debugProcess.virtualMachineProxy
                val notebookClass = virtualMachine.classesByNameProvider.get("org.jetbrains.kotlinx.jupyter.NotebookImpl").firstOrNull() ?: return@invoke
                val notebookReference = notebookClass.instances(1).firstOrNull() ?: return@invoke
                // make it field first
                //val getter = notebookClass.methodsByName("getVariablesState").firstOrNull() ?: return@invoke
                val variablesHolderReference = getVariablesHolderReference(notebookReference, notebookClass) ?: return@invoke

                node.populateWithVariables(variablesHolderReference, null, debugProcess.debuggerContext)
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

    private fun getVariablesHolderReference(notebookReference: ObjectReference, declaringClass: ReferenceType): ObjectReference? {
        val sharedContextField = declaringClass.fieldByName("sharedReplContext")
        val sharedContextReference = notebookReference.getValue(sharedContextField) as ObjectReference
        val evaluatorField = sharedContextReference.referenceType().fieldByName("evaluator")
        val evaluatorImpl = sharedContextReference.getValue(evaluatorField) as ObjectReference

        val variablesHolderField = evaluatorImpl.referenceType().fieldByName("variablesHolder")
        return evaluatorImpl.getValue(variablesHolderField) as ObjectReference
    }


    private fun XCompositeNode.populateWithVariables(
      variablesStateReference: ObjectReference,
      evaluationContext: EvaluationContextImpl?,
      debuggerContext: DebuggerContextImpl
    ) {
        fun retrieveMapEntryReferenceAndVariablesSize(): Pair<ObjectReference?, Int> {
            val declaringClass = variablesStateReference.referenceType()
            if (declaringClass.name() != "java.util.LinkedHashMap") {
                throw UnsupportedOperationException("VariablesState shall be an instance of LinkedMap")
            }
            val currentSize = (variablesStateReference.getValue(declaringClass.fieldByName("size")) as? IntegerValue)?.value()
            val headField = declaringClass.fieldByName("head")
            if (currentSize == null) {
                throw IllegalArgumentException("Could not retrieve size from VariablesState")
            }
            return variablesStateReference.getValue(headField) as? ObjectReference to currentSize
        }

        val (mapEntryReference, stateSize) = retrieveMapEntryReferenceAndVariablesSize()
        if (mapEntryReference == null) return
        val mapEntryReferenceType = mapEntryReference.referenceType()
        val hashMapNodeType = (mapEntryReferenceType as ClassType).superclass()
        if (hashMapNodeType == null) {
            throw IllegalArgumentException("Parent interface for Entry shall not be null")
        }

        val nextEntryFieldAccessor = mapEntryReferenceType.fieldByName("after")

        addChildren(
            XValueChildrenList()
                .apply {
                    addInternalVariables(
                        stateSize,
                        VariablesStateAccessorData(
                            nextEntryFieldAccessor, mapEntryReference, hashMapNodeType
                        ),
                        evaluationContext, debuggerContext
                    )
                    },
            true)
    }


    private fun XValueChildrenList.addInternalVariables(
        variablesStateSize: Int,
        accessorData: VariablesStateAccessorData,
        evaluationContext: EvaluationContextImpl?,
        debuggerContext: DebuggerContextImpl
    ) {
        var mapEntryReference = accessorData.mapEntryReference
        val nextEntryField = accessorData.nextEntryFieldAccessor
        val keyField = accessorData.hashMapNodeClassType.fieldByName("key")
        val valueField = accessorData.hashMapNodeClassType.fieldByName("value")
        val manager = debuggerContext.debugProcess?.xdebugProcess?.nodeManager


        for (i in 0 until variablesStateSize) {
            val keyReference = mapEntryReference.getValue(keyField) as? StringReference ?: continue
            val (variableStateReference, fieldAccessor) = mapEntryReference
                        .getValue(valueField)
                        .retrieveValueFromVariableState(keyReference.value())

            if (variableStateReference == null) continue

            add(keyReference.value(),
                NotebookVariableFieldValue(null,
                                      // maybe change factory
                                      NotebookVariableStateDescriptor(
                                          debuggerContext.debuggerSession!!,
                                          debugSession.virtualFile,
                                          project, variableStateReference, fieldAccessor,
                                          variableStateReference.getValue(fieldAccessor)
                                      ),
                                      debuggerContext.debugProcess!!, manager, false
                )
            )
            (mapEntryReference.getValue(nextEntryField) as? ObjectReference?)?.let {
                mapEntryReference = it
            }
        }
    }

    private fun Value.retrieveValueFromVariableState(fieldName: String): Pair<ObjectReference?, Field> {
        this as ObjectReference
        if (referenceType().name() != "org.jetbrains.kotlinx.jupyter.api.VariableStateImpl") {
            throw IllegalArgumentException("Value retrieval is possible from instances of VariableStateImpl")
        }

        // another way round by scriptInstance and field reflection
        val scriptInstance = referenceType().fieldByName("scriptInstance").let {
            getValue(it) as ObjectReference
        }
        val suitableFieldAccessor = scriptInstance.referenceType().fieldByName(fieldName)

        return scriptInstance to suitableFieldAccessor
    }
}