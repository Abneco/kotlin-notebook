// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.variables

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl

/**
 * Represents a runtime environment explorer of the a [com.intellij.debugger.engine.DebugProcess]
 * for a Kotlin Notebook session.
 *
 * NB: right now it works under an assumption for a separate notebook process.
 *
 */
internal interface NotebookAbstractSessionRuntimeEnvironmentExplorer {
    /**
     * Returns a reference to the NotebookImpl instance mirror.
     */
    fun getNotebookReferenceProxy(virtualMachineProxy: VirtualMachineProxy): NotebookJdiProxy?

    /**
     * Returns a reference to the VariablesState instance mirror.
     */
    fun getVariablesStateReferenceProxy(virtualMachineProxy: VirtualMachineProxy): Map<String, VariableStateJdiProxy>?

    /**
     * Builds a debugger-api container [XValueChildrenList] for all the variables in the VariablesState.
     */
    fun buildXValueListForVariablesState(virtualMachineProxy: VirtualMachineProxy, evaluationContext: EvaluationContextImpl): XValueChildrenList

    /**
     * Returns an existing [JavaValue] by its name inside the VariablesState,
     * or null otherwise.
     */
    fun getVariableValueByNameOrNull(name: String): JavaValue?
}