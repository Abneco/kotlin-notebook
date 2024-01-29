// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableStateDescriptor
import org.jetbrains.kotlinx.jupyter.plugin.debug.frame.KotlinNotebookVariablesFrame
import org.jetbrains.kotlinx.jupyter.plugin.debug.frame.NotebookVariableFieldValue
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.completion.JupyterRuntimeProcessListener

class NotebookVariablesPerFileState(
    private val virtualFile: BackedNotebookVirtualFile,
    private val coroutineScope: CoroutineScope,
    parentDisposable: Disposable
) : NotebookAbstractSessionEnvironmentExplorer, Disposable {
    companion object {
        private val LOG = thisLogger()
    }

    init {
        Disposer.register(parentDisposable, this)
    }
    private val notebookSessionEnvironmentProvider = NotebookSessionNoSuspensionEnvironmentProvider(virtualFile)

    fun requestVariablesUpdate(project: Project) {
        coroutineScope.async {
            project.messageBus.syncPublisher(JupyterRuntimeProcessListener.TOPIC)
                .notebookSessionEnvironmentUpdated(virtualFile.file, null)
        }
    }

    override fun getNotebookReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference? {
        return notebookSessionEnvironmentProvider.notebookReferenceProvider(virtualMachineProxy)
    }

    override fun getVariablesStateReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference? {
        return notebookSessionEnvironmentProvider.variableStateReferenceProvider(virtualMachineProxy)
    }

    override fun representVariablesStateAsXContainer(virtualMachineProxy: VirtualMachineProxy, evaluationContext: EvaluationContextImpl?): XValueChildrenList {
        fun XValueChildrenList.addInternalVariables(
            variablesStateSize: Int,
            accessorData: KotlinNotebookVariablesFrame.Companion.VariablesStateAccessorData,
            debuggerContext: DebuggerContextImpl
        ) {
            var mapEntryReference = accessorData.mapEntryReference
            val nextEntryField = accessorData.nextEntryFieldAccessor
            val keyField = accessorData.hashMapNodeClassType.fieldByName("key")
            val valueField = accessorData.hashMapNodeClassType.fieldByName("value")
            val nodeManager = debuggerContext.debugProcess?.xdebugProcess?.nodeManager


            for (i in 0 until variablesStateSize) {
                val keyReference = mapEntryReference.getValue(keyField) as? StringReference ?: continue
                val variableStateValued = mapEntryReference.getValue(valueField)
                val (variableStateReference, fieldAccessor) = notebookSessionEnvironmentProvider
                    .variableValueFromStateProvider(
                        variableStateValued, keyReference.value()
                    )

                if (variableStateReference == null) continue

                val xValue = if (evaluationContext != null) {
                    JavaValue.create(
                        null,
                        nodeManager?.getFieldDescriptor(
                            null,
                            variableStateReference,
                            fieldAccessor
                        )!!,
                        evaluationContext,
                        nodeManager,
                        false
                    )
                } else {
                    NotebookVariableFieldValue(
                        null,
                        NotebookVariableStateDescriptor(
                           debuggerContext.debuggerSession!!,
                           virtualFile,
                           virtualMachineProxy.debugProcess.project, variableStateReference, fieldAccessor,
                           variableStateReference.getValue(fieldAccessor)
                        ),
                        debuggerContext.debugProcess!!, nodeManager, false
                    )
                }

                add(keyReference.value(), xValue)

                (mapEntryReference.getValue(nextEntryField) as? ObjectReference?)?.let {
                    mapEntryReference = it
                }
            }
        }

        val list = XValueChildrenList()
        if (virtualMachineProxy !is VirtualMachineProxyImpl) return list

        val variablesHolderReference = getVariablesStateReference(virtualMachineProxy) ?: return list

        val (mapEntryReference, stateSize) = notebookSessionEnvironmentProvider.variableStateFirstEntryAndSizeProvider(variablesHolderReference)
        if (mapEntryReference == null) return list
        val mapEntryReferenceType = mapEntryReference.referenceType()
        val hashMapNodeType = (mapEntryReferenceType as ClassType).superclass()
        if (hashMapNodeType == null) {
            throw IllegalArgumentException("Parent interface for Entry shall not be null")
        }

        val nextEntryFieldAccessor = mapEntryReferenceType.fieldByName("after")
        val processImpl = virtualMachineProxy.debugProcess as? DebugProcessImpl ?: return list

        return list.apply {
            addInternalVariables(
                stateSize,
                KotlinNotebookVariablesFrame.Companion.VariablesStateAccessorData(
                    nextEntryFieldAccessor, mapEntryReference, hashMapNodeType
                ),
                processImpl.debuggerContext
            )
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
    }
}