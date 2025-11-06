// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.debug.proxy.notebook.JdiNotebookExtension
import com.intellij.kotlin.jupyter.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.VariableStateJdiProxy
import com.sun.jdi.ObjectReference

/**
 * Base handler of [JdiNotebookExtension] for [NotebookJdiProxy].
 *
 */
class JdiNotebookDelegateHandler(
    valueContext: DebugValueContext,
) : JdiNotebookExtension {
    private val notebookProxy by lazy {
        createJdiObjectProxy<NotebookJdiProxy>(valueContext)
    }

    override val debugProcess: DebugProcessImpl = valueContext.debugProcess
    override val objectReference: ObjectReference = valueContext.objectReference
    private val evaluationContext by lazy { valueContext.evaluationContext }

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
            DebugValueContext(
                debugProcess,
                variablesHolderRef,
                evaluationContext
            )
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
                DebugValueContext(
                    debugProcess,
                    valueRef,
                    evaluationContext
                )
            )
            result[keyString] = variableStateProxy
        }

        return result
    }
}