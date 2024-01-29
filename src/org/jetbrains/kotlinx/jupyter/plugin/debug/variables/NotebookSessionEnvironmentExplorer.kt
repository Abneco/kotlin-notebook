// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.xdebugger.frame.XValueChildrenList
import com.sun.jdi.ObjectReference

internal interface NotebookAbstractSessionEnvironmentExplorer {
    fun getNotebookReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference?

    fun getVariablesStateReference(virtualMachineProxy: VirtualMachineProxy): ObjectReference?

    fun representVariablesStateAsXContainer(virtualMachineProxy: VirtualMachineProxy, evaluationContext: EvaluationContextImpl?): XValueChildrenList
}