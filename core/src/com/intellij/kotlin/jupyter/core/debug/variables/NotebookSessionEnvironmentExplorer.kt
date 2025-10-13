// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.variables

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ObjectReference

/**
 * Represents a runtime environment explorer of the a [com.intellij.debugger.engine.DebugProcess]
 * for a Kotlin Notebook session.
 *
 * NB: right now it works under an assumption for a separate notebook process.
 *
 * TODO: it's better to use typed proxy instances instead of [ObjectReference].
 */
internal interface NotebookAbstractSessionRuntimeEnvironmentExplorer {
    /**
     * Returns a reference to the NotebookImpl instance mirror.
     */
    fun getNotebookReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference?

    /**
     * Returns a reference to the VariablesState instance mirror.
     */
    fun getVariablesStateReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference?

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