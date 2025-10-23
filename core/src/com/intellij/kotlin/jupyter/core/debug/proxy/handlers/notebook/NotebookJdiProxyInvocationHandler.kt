// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections.LinkedHashMapJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.ObjectReference
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Specialized [java.lang.reflect.InvocationHandler] for [com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy] that delegates to base handler
 * but provides custom logic for the variablesHolderProxy accessor.
 *
 * Special methods are typically made in a way that values are wrapped in a proxy to allow JDI-like access to remote objects.
 */
internal class NotebookJdiProxyInvocationHandler(
  debugProcess: DebugProcessImpl,
  objectReference: ObjectReference
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, objectReference) {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        // Delegate other to base handler
        if (proxy !is NotebookJdiProxy) {
            return super.invoke(proxy, method, args)
        }

        // Special handling for unique methods
        return when (method.name) {
            "getVariablesHolderProxy" -> getVariablesHolderProxyMap(proxy)
            "getVariablesHolderReference" -> proxy.getVariablesHolderReference()
            else -> super.invoke(proxy, method, args)
        }
    }

    /**
     * Actual type the variablesHolder field from the remote Notebook object
     * is expected to be a [LinkedHashMap]<String, VariableState>,
     * which later on wrapped in a [com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy].
     */
    private fun getVariablesHolderProxyMap(proxy: NotebookJdiProxy): Map<String, VariableStateJdiProxy> {
        val variablesHolderRef = proxy.getVariablesHolderReference()
            ?: return emptyMap()

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

    private fun NotebookJdiProxy.getVariablesHolderReference(): ObjectReference? {
        val replContext = sharedReplContext ?: return null
        val evaluatorObjectReference = replContext.evaluator.objectReference
        return evaluatorObjectReference.getFieldValueByName("variablesHolder") as? ObjectReference
    }
}