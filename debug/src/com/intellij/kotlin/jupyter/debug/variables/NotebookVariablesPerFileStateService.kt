// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.variables

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.variables.common.JupyterEnvironmentUpdateListener
import com.intellij.kotlin.jupyter.core.editor.appearance.data.KotlinNotebookVariablesToolWindowConfiguration
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.createDisposableChild
import com.intellij.kotlin.jupyter.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.VariableStateJdiProxy
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.variables.context.NotebookAbstractSessionRuntimeEnvironmentExplorer
import com.intellij.kotlin.jupyter.debug.variables.context.NotebookSessionNoSuspensionValuesProxyFinder
import com.intellij.kotlin.jupyter.debug.variables.presentation.KotlinNotebookToolVariablesWindowHandler
import com.intellij.kotlin.jupyter.debug.variables.presentation.KotlinNotebookVarsToolWindow
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XValueChildrenList
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
) : NotebookPerFileChildService(virtualFile, coroutineScope), NotebookAbstractSessionRuntimeEnvironmentExplorer {
    companion object {
        private val LOG = notebookLogger()
    }

    private val variableToolWindowHandler = createDisposableChild {
        KotlinNotebookToolVariablesWindowHandler()
    }
    private val notebookDebugSession = KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)

    private val notebookSessionValuesProvider = NotebookSessionNoSuspensionValuesProxyFinder(notebookDebugSession)

    private val currentFrameProxy: StackFrameProxyImpl?
        get() = notebookDebugSession.currentStackFrameProxy

    private val xValuesByName: MutableMap<String, JavaValue> = mutableMapOf()

    var variablesMetaData: Map<String, String?>? = null
        private set

    fun updateSnippetsMetaData(evaluatedSnippetMetadata: EvaluatedSnippetMetadata) {
        variablesMetaData = evaluatedSnippetMetadata.evaluatedVariablesState
    }

    fun getToolWindow(
        setupData: KotlinNotebookVariablesToolWindowConfiguration
    ): KotlinNotebookVarsToolWindow {
        return variableToolWindowHandler
            .getOrCreateToolWindow(setupData)
    }

    fun requestVariablesUpdate() {
        coroutineScope.async {
            project.messageBus.syncPublisher(JupyterEnvironmentUpdateListener.TOPIC)
                .onRuntimeEnvironmentUpdate(virtualFile, null)
        }
    }

    fun getXValueChildrenList(): XValueChildrenList? {
        val debugSession = KotlinNotebookDebugSessionManager.getForFile(project, virtualFile)
        val vmProxy = debugSession.currentStackFrameProxy?.virtualMachine
        val evalContext = debugSession.evaluationContext
        if (vmProxy == null || evalContext == null) {
            return null
        }
        return buildXValueListForVariablesState(vmProxy, evalContext)
    }

    override fun getNotebookReferenceProxy(): NotebookJdiProxy? {
        val virtualMachineProxy = currentFrameProxy?.virtualMachine ?: return null
        return notebookSessionValuesProvider.notebookProxyProvider(virtualMachineProxy)
    }

    override fun getVariablesStateReferenceProxy(): Map<String, VariableStateJdiProxy>? {
        val virtualMachineProxy = currentFrameProxy?.virtualMachine ?: return null
        return notebookSessionValuesProvider.variablesStateProvider(virtualMachineProxy)?.apply {
            val variablesNames = keys
            for (variableName in variablesNames) {
                val proxy = get(variableName) ?: continue
                proxy.bindValueFromRuntime(
                    getVariableValueByNameOrNull(variableName)
                )
            }
        }
    }

    override fun getVariableValueByNameOrNull(name: String): JavaValue? {
        return xValuesByName[name]
    }

    override fun buildXValueListForVariablesState(virtualMachineProxy: VirtualMachineProxy, evaluationContext: EvaluationContextImpl): XValueChildrenList {
        fun XValueChildrenList.populateFrameWithVariables(
            variablesState: Map<String, VariableStateJdiProxy>,
            debuggerContext: DebuggerContextImpl
        ) {
            val nodeManager = debuggerContext.debugProcess?.xdebugProcess?.nodeManager
            xValuesByName.clear()

            for ((variableName, valueProxy) in variablesState) {
                val fieldAccessor = valueProxy.findVariableField(variableName) ?: continue
                val scriptInstance = valueProxy.scriptInstanceReference ?: continue
                val fieldDescriptor = nodeManager?.getFieldDescriptor(
                    null,
                    scriptInstance,
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
                xValuesByName[variableName] = xValue

                add(variableName, xValue)
            }
        }

        val list = XValueChildrenList()
        if (virtualMachineProxy !is VirtualMachineProxyImpl) return list
        if (!virtualMachineProxy.canBeModified() || !virtualMachineProxy.debugProcess.isAttached) return list

        val variablesHolderProxy = notebookSessionValuesProvider.variablesStateProvider(virtualMachineProxy) ?: return list
        val processImpl = virtualMachineProxy.debugProcess ?: return list

        return list.apply {
            processImpl.invokeInManagerThread {
                populateFrameWithVariables(
                    variablesHolderProxy,
                    processImpl.debuggerContext
                )
            }
        }
    }

    fun clear() {
        variablesMetaData = null
        xValuesByName.clear()
    }

    override fun dispose() {
        coroutineScope.cancel()
        clear()
    }
}