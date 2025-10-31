// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiFieldAccessPath
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.convertToJdiValue
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.findFieldNameByGetterOrNull
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * Base class for [java.lang.reflect.InvocationHandler] implementations
 * that delegate to a JDI [ObjectReference]
 */
abstract class JdiProxyInvocationHandlerBase(
    protected val debugProcess: DebugProcessImpl,
    protected val objectReference: ObjectReference,
    apiExtensionHandler: JdiProxyApiExtension,
) : InvocationHandler {
    protected fun findJdiMethod(refType: ReferenceType, methodName: String, paramTypes: Array<Class<*>>): com.sun.jdi.Method? {
        val methods = refType.allMethods()

        return methods.firstOrNull { jdiMethod ->
            jdiMethod.name() == methodName && jdiMethod.argumentTypes().size == paramTypes.size
        }
    }

    /**
     * Handler for [JdiProxyApiExtension] methods.
     * Override this property in subclasses to provide custom extension logic.
     */
    protected open val extensionHandler: JdiProxyApiExtension? = apiExtensionHandler

    /**
     * This method requires an active breakpoint to be visible inside the IJ debugger.
     * Now, we don't have any.
     */
    @Suppress("UNUSED")
    private fun invokeMethod(jdiMethod: com.sun.jdi.Method, args: Array<out Any>?): Value? {
        // Get any suspended thread to invoke method
        val thread = objectReference.virtualMachine().allThreads().firstOrNull { it.isSuspended }
            ?: throw IllegalStateException("No suspended threads available for method invocation")

        // Convert to JDI
        val jdiArgs = args?.map { convertToJdiValue(it) } ?: emptyList()

        // Invoke method on remote object
        val suspendContext = debugProcess.suspendManager.pausedContexts.firstOrNull { it.thread == thread } ?: return null
        val evaluationContext = suspendContext.evaluationContext ?: return null
        val result = debugProcess.invokeMethod(
            evaluationContext,
            objectReference,
            jdiMethod,
            jdiArgs
        )

        return result
    }

    /**
     * Traverses the inheritance hierarchy of the object to find a field with the given name.
     */
    protected fun getViaFieldAccessPath(method: Method): Value? {
        val pathAnnotation = method.getAnnotation(JdiFieldAccessPath::class.java)?.path ?: return null
        val path = pathAnnotation.split(".")
        if (path.isEmpty()) return null

        var current: Value? = objectReference
        for (fieldName in path) {
            if (current !is ObjectReference) return null
            current = current.getFieldValueByName(fieldName)
        }

        return current
    }

    protected fun invokeAsFieldAccess(method: Method): Value? {
        val byFieldAccessPath = getViaFieldAccessPath(method)
        if (byFieldAccessPath != null) return byFieldAccessPath

        val fieldName = method.findFieldNameByGetterOrNull() ?: return null
        return objectReference.getFieldValueByName(fieldName)
    }

    protected fun convertToJdiValue(value: Any): Value {
        val vm = objectReference.virtualMachine()
        return value.convertToJdiValue(vm)
    }
}