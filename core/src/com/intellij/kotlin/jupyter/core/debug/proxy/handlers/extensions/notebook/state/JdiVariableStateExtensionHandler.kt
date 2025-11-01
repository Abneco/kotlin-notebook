// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.extensions.notebook.state

import com.intellij.debugger.collections.visualizer.core.backend.XCollectionAccessor
import com.intellij.debugger.collections.visualizer.core.backend.XCollectionAccessorProvider
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.JdiVariableStateExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.util.findFieldByName
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference

/**
 * Base handler for [JdiVariableStateExtension] extension API.
 *
 * @see [VariableStateJdiProxyInvocationHandler]
 */
internal class JdiVariableStateExtensionHandler(
    override val objectReference: ObjectReference
) : JdiVariableStateExtension {
    @Volatile
    private var javaValue: JavaValue? = null

    override val renderedText: String?
        get() = javaValue?.descriptor?.valueText

    override val variableValueObjectReference: ObjectReference?
        get() = findVariableValueObjectReference()

    override fun updateFromRuntimeContext(javaValue: JavaValue?) {
        this.javaValue = javaValue
    }

    override fun findVariableField(name: String): Field? {
        val scriptInstance = getScriptInstance()
            ?: throw IllegalStateException("Cannot retrieve 'scriptInstance' field from VariableState")

        // Find field by name in scriptInstance's reference type
        return scriptInstance.findFieldByName(name)
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