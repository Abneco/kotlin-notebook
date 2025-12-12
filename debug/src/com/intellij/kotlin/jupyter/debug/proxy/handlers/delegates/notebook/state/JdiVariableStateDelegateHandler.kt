// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.state

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyDescriptorAwareBaseHandler
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.JdiVariableStateExtension
import com.intellij.kotlin.jupyter.debug.util.findFieldByName
import com.intellij.kotlin.jupyter.debug.util.getFieldValueByName
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import java.util.concurrent.atomic.AtomicReference

/**
 * Base handler for [JdiVariableStateExtension] extension API.
 *
 */
internal class JdiVariableStateDelegateHandler(
    private val valueContext: DebugValueContext,
) : JdiVariableStateExtension, JdiProxyDescriptorAwareBaseHandler(valueContext) {
    // Cache for variable value bound to the current descriptor.
    private val cachedVariableRef: AtomicReference<Value?> = AtomicReference(null)

    private fun invalidateValueCache() {
        cachedVariableRef.set(null)
    }

    /**
     * Invalidates cache when a new descriptor is bound (i.e., JavaValue changes).
     */
    override fun bindDescriptor(valueDescriptor: ValueDescriptorImpl) {
        super<JdiProxyDescriptorAwareBaseHandler>.bindDescriptor(valueDescriptor)
        invalidateValueCache()
    }

    override fun bindValueFromRuntime(value: JavaValue?) {
        super<JdiProxyDescriptorAwareBaseHandler>.bindValueFromRuntime(value)
        invalidateValueCache()
    }

    override val variableValue: Value?
        get() = findVariableValue()

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
        val underlyingValue = variableValue as? ObjectReference ?: return null
        val context = DebugValueContext(
            debugProcess,
            underlyingValue,
            valueContext.evaluationContext
        )

        return createJdiObjectProxy(context, proxyType) as T?
    }

    private fun findVariableValue(): Value? {
        val currentDescriptor = javaValue?.descriptor
        if (currentDescriptor == null) {
            invalidateValueCache()
            return null
        }

        val cachedVariable = cachedVariableRef.get()
        if (cachedVariable != null) {
            return cachedVariable
        }

        val computed = findVariableValue(currentDescriptor)
        cachedVariableRef.set(computed)
        return computed
    }

    private fun getScriptInstance(): ObjectReference? {
        return objectReference.getFieldValueByName("scriptInstance") as? ObjectReference
    }

    private fun findVariableValue(valueDescriptor: ValueDescriptor): Value? {
        val variableName = valueDescriptor.name
        return getScriptInstance()?.getFieldValueByName(variableName)
    }
}