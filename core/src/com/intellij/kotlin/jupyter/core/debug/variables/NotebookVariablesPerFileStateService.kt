// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.variables

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.createDisposableChild
import com.intellij.kotlin.jupyter.core.variables.KotlinNotebookToolWindowHandler
import com.intellij.kotlin.jupyter.core.variables.KotlinNotebookVarsToolWindow
import com.intellij.kotlin.jupyter.core.variables.NotebookVariablesToolWindowSetup
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.StringReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata

/**
 * This service is created for every Kotlin notebook file
 * and provides an entry point for variables:
 *  - retrieving variables from [VirtualMachineProxy]
 *  - delegating creating and updates of corresponding VariableToolWindowPanel
 */
class NotebookVariablesPerFileStateService(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    coroutineScope: CoroutineScope,
) : NotebookPerFileChildService(virtualFile, coroutineScope), NotebookAbstractSessionEnvironmentExplorer {
    companion object {
        private val LOG = notebookLogger()

        data class VariablesStateAccessorData(
            val nextEntryFieldAccessor: Field,
            var mapEntryReference: ObjectReference,
            val hashMapNodeClassType: ClassType
        )
    }

    private val variableToolWindowHandler = createDisposableChild {
        KotlinNotebookToolWindowHandler()
    }
    private val notebookSessionEnvironmentProvider = NotebookSessionNoSuspensionEnvironmentProvider(virtualFile)
    var variablesMetaData: Map<String, String?>? = null
        private set

    fun updateSnippetsMetaData(evaluatedSnippetMetadata: EvaluatedSnippetMetadata) {
        variablesMetaData = evaluatedSnippetMetadata.evaluatedVariablesState
    }

    fun isToolWindowReady(): Boolean {
        return variableToolWindowHandler.isToolWindowReady
    }

    fun getToolWindow(
        setupData: NotebookVariablesToolWindowSetup? = null
    ): KotlinNotebookVarsToolWindow {
        return variableToolWindowHandler
            .getOrCreateToolWindow(project, virtualFile, setupData)
    }

    fun requestVariablesUpdate() {
        coroutineScope.async {
            project.messageBus.syncPublisher(JupyterEnvironmentUpdateListener.TOPIC)
                .onJupyterEnvironmentUpdated(virtualFile, null)
        }
    }

    override fun getNotebookReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference? {
        return notebookSessionEnvironmentProvider.notebookReferenceProvider(virtualMachineProxy)
    }

    override fun getVariablesStateReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference? {
        return notebookSessionEnvironmentProvider.variableStateReferenceProvider(virtualMachineProxy)
    }

    override fun getXValueChildrenList(): XValueChildrenList? {
        val debugSession = KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)
        val vmProxy = debugSession.currentStackFrameProxy?.virtualMachine
        val evalContext = debugSession.evaluationContext
        if (vmProxy == null || evalContext == null) {
            return null
        }
        return representVariablesStateAsXContainer(vmProxy, evalContext)
    }

    override fun getVariableValueByNameOrNull(name: String): JavaValue? {
        val variables = getXValueChildrenList() ?: return null
        var foundVariable: JavaValue? = null
        for (i in 0 until variables.size()) {
            val varName = variables.getName(i)
            if (varName == name) {
                foundVariable = variables.getValue(i) as? JavaValue
                break
            }
        }
        return foundVariable
    }

    override fun representVariablesStateAsXContainer(virtualMachineProxy: VirtualMachineProxy, evaluationContext: EvaluationContextImpl): XValueChildrenList {
        fun XValueChildrenList.addInternalVariables(
            variablesStateSize: Int,
            accessorData: VariablesStateAccessorData,
            debuggerContext: DebuggerContextImpl
        ) {
            var mapEntryReference = accessorData.mapEntryReference
            val nextEntryField = accessorData.nextEntryFieldAccessor
            val keyField = DebuggerUtils.findField(accessorData.hashMapNodeClassType, "key")
            val valueField = DebuggerUtils.findField(accessorData.hashMapNodeClassType, "value")
            val nodeManager = debuggerContext.debugProcess?.xdebugProcess?.nodeManager


            for (i in 0 until variablesStateSize) {
                val keyReference = mapEntryReference.getValue(keyField) as? StringReference ?: continue
                val variableStateValued = mapEntryReference.getValue(valueField)
                val (variableStateReference, fieldAccessor) = notebookSessionEnvironmentProvider
                    .variableValueFromStateProvider(
                        variableStateValued, keyReference.value()
                    )

                if (variableStateReference == null) continue
                val fieldDescriptor = nodeManager?.getFieldDescriptor(
                    null,
                    variableStateReference,
                    fieldAccessor
                )
                if (fieldDescriptor == null) {
                    LOG.warn("Can't find descriptor for $fieldAccessor")
                    return
                }

                val xValue = JavaValue.create(
                    null,
                    fieldDescriptor,
                    evaluationContext,
                    nodeManager,
                    false
                )

                add(keyReference.value(), xValue)

                (mapEntryReference.getValue(nextEntryField) as? ObjectReference?)?.let {
                    mapEntryReference = it
                }
            }
        }

        val list = XValueChildrenList()
        if (virtualMachineProxy !is VirtualMachineProxyImpl) return list
        if (!virtualMachineProxy.canBeModified()) return list

        val variablesHolderReference = getVariablesStateReference(virtualMachineProxy) ?: return list

        val (mapEntryReference, stateSize) = notebookSessionEnvironmentProvider.variableStateFirstEntryAndSizeProvider(variablesHolderReference)
        if (mapEntryReference == null) return list
        val mapEntryReferenceType = mapEntryReference.referenceType()
        val hashMapNodeType = (mapEntryReferenceType as ClassType).superclass()
        if (hashMapNodeType == null) {
            throw IllegalArgumentException("Parent interface for Entry shall not be null")
        }

        val nextEntryFieldAccessor = DebuggerUtils.findField(mapEntryReferenceType, "after") ?: return list
        val processImpl = virtualMachineProxy.debugProcess ?: return list

        return list.apply {
            processImpl.invokeInManagerThread {
                addInternalVariables(
                    stateSize,
                    VariablesStateAccessorData(
                        nextEntryFieldAccessor, mapEntryReference, hashMapNodeType
                    ),
                    processImpl.debuggerContext
                )
            }
        }
    }

    fun clear() {
        variablesMetaData = null
    }

    override fun dispose() {
        coroutineScope.cancel()
        clear()
    }
}