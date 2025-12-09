// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.state

import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyDescriptorAwareBaseHandler
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.JdiVariableStateExtension
import com.intellij.kotlin.jupyter.debug.util.findFieldByName
import com.intellij.kotlin.jupyter.debug.util.getFieldValueByName
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference

/**
 * Base handler for [JdiVariableStateExtension] extension API.
 *
 */
internal class JdiVariableStateDelegateHandler(
    private val valueContext: DebugValueContext,
) : JdiVariableStateExtension, JdiProxyDescriptorAwareBaseHandler(valueContext) {
    override val variableValueObjectReference: ObjectReference?
        get() = findVariableValueObjectReference()

    override fun findVariableField(name: String): Field? {
        val scriptInstance = getScriptInstance()
            ?: throw IllegalStateException("Cannot retrieve 'scriptInstance' field from VariableState")

        // Find field by name in scriptInstance's reference type
        return scriptInstance.findFieldByName(name)
    }

    override fun <T : Any> createProxyForValue(proxyType: Class<T>): T? {
        require(proxyType.isInterface) {
            "Type parameter T must be an interface, got ${proxyType}"
        }
        val underlyingValue = variableValueObjectReference ?: return null
        val context = DebugValueContext(
            debugProcess,
            underlyingValue,
            valueContext.evaluationContext
        )

        return createJdiObjectProxy(context, proxyType) as T?
    }

    private fun findVariableValueObjectReference(): ObjectReference? {
        val descriptor = javaValue?.descriptor ?: return null
        return findVariableValue(descriptor)
    }

    private fun getScriptInstance(): ObjectReference? {
        return objectReference.getFieldValueByName("scriptInstance") as? ObjectReference
    }

    private fun findVariableValue(valueDescriptor: ValueDescriptor): ObjectReference? {
        val variableName = valueDescriptor.name
        return getScriptInstance()?.getFieldValueByName(variableName) as? ObjectReference
    }
}