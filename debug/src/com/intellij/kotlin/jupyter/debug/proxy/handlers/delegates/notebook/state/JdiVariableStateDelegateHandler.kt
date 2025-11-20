// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.state

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.tree.ValueDescriptor
import com.intellij.kotlin.jupyter.debug.proxy.DebugValueContext
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
    valueContext: DebugValueContext,
) : JdiVariableStateExtension {
    override val debugProcess: DebugProcessImpl = valueContext.debugProcess

    override val objectReference: ObjectReference = valueContext.objectReference

    @Volatile
    override var javaValue: JavaValue? = null

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