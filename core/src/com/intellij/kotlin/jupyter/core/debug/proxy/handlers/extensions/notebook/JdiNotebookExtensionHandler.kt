// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.extensions.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.NotebookJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.JdiNotebookExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.sun.jdi.ObjectReference

/**
 * Base handler of [JdiNotebookExtension] for [NotebookJdiProxy].
 *
 * @see [NotebookJdiProxyInvocationHandler]
 */
class JdiNotebookExtensionHandler(
    private val debugProcess: DebugProcessImpl,
    override val objectReference: ObjectReference,
) : JdiNotebookExtension {
    private val notebookProxy by lazy {
        createJdiObjectProxy<NotebookJdiProxy>(debugProcess, objectReference)
    }

    override val variablesHolderProxy: Map<String, VariableStateJdiProxy>
        get() = getVariablesHolderProxyMap()

    /**
     * Actual type the variablesHolder field from the remote Notebook object
     * is expected to be a [LinkedHashMap]<String, VariableState>,
     * which later on wrapped in a [VariableStateJdiProxy].
     */
    private fun getVariablesHolderProxyMap(): Map<String, VariableStateJdiProxy> {
        val variablesHolderRef = notebookProxy.variablesHolderReference

        if (!variablesHolderRef.isLinkedHashMap()) {
            throw IllegalStateException("Expected variablesHolder to be a LinkedHashMap, got ${variablesHolderRef.referenceType().name()}")
        }

        val mapProxy = createJdiObjectProxy<Map<*, *>>(
            debugProcess,
            variablesHolderRef
        )

        val result = mutableMapOf<String, VariableStateJdiProxy>()

        for ((key, value) in mapProxy.entries) {
            val keyString = key as? String ?: continue
            val valueRef = when (value) {
                is JdiObjectReferenceProxy -> value.objectReference
                is ObjectReference -> value
                else -> continue
            }

            val variableStateProxy = createJdiObjectProxy<VariableStateJdiProxy>(
                debugProcess,
                valueRef
            )
            result[keyString] = variableStateProxy
        }

        return result
    }
}