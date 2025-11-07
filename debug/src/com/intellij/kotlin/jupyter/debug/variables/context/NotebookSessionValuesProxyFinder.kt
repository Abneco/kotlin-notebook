// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.variables.context

import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.VariableStateJdiProxy
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl

/**
 * This class is responsible for finding and creating [JdiObjectReferenceProxy]'s
 * for the current [com.intellij.debugger.engine.DebugProcessImpl].
 */
internal sealed interface NotebookSessionValuesProxyFinder {
    val notebookProxyProvider: (VirtualMachineProxy) -> NotebookJdiProxy?
    val variablesStateProvider: (VirtualMachineProxy) -> Map<String, VariableStateJdiProxy>?
}

internal class NotebookSessionNoSuspensionValuesProxyFinder(
    private val virtualFile: BackedNotebookVirtualFile
) : NotebookSessionValuesProxyFinder {
    override val notebookProxyProvider: (VirtualMachineProxy) -> NotebookJdiProxy?
        get() = ::retrieveNotebookProxy
    override val variablesStateProvider: (VirtualMachineProxy) -> Map<String, VariableStateJdiProxy>?
        get() = ::retrieveVariablesStateProxy

    private fun retrieveNotebookProxy(virtualMachine: VirtualMachineProxy): NotebookJdiProxy? {
        if (virtualMachine !is VirtualMachineProxyImpl) return null

        val notebookClass = virtualMachine.classesByNameProvider
            .get("${NotebookImpl::class.java.name}")
            .firstOrNull() ?: return null
        val notebookRef = notebookClass.instances(1).firstOrNull() ?: return null

        return createJdiObjectProxy<NotebookJdiProxy>(
          virtualMachine.debugProcess, objectReference = notebookRef
        )
    }

    private fun retrieveVariablesStateProxy(virtualMachine: VirtualMachineProxy): Map<String, VariableStateJdiProxy>? {
        val notebookProxy = retrieveNotebookProxy(virtualMachine) ?: return null
        return notebookProxy.variablesHolderProxy
    }
}