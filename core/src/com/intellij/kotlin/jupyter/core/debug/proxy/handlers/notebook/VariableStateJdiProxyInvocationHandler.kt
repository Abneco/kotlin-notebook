// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.util.findFieldByName
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import java.lang.reflect.Method

/**
 * Specialized [java.lang.reflect.InvocationHandler] for invoking custom methods on [com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy].
 */
internal class VariableStateJdiProxyInvocationHandler(
  debugProcess: DebugProcessImpl,
  objectReference: ObjectReference
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, objectReference) {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return when (method.name) {
            "findVariableField" -> {
                val name = args?.firstOrNull() as? String
                findVariableField(name)
            }
            // Delegate to base handler
            else -> super.invoke(proxy, method, args)
        }
    }

    /**
     * Retrieves the Field object for this variable from the scriptInstance by name.
     */
    private fun findVariableField(variableName: String?): Field? {
        if (variableName == null) {
            return null
        }

        val scriptInstance = objectReference.getFieldValueByName("scriptInstance") as? ObjectReference
            ?: throw IllegalStateException("Cannot retrieve 'scriptInstance' field from VariableState")

        // Find field by name in scriptInstance's reference type
        return scriptInstance.findFieldByName(variableName)
    }
}