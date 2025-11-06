// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.JdiFieldAccessPath
import com.intellij.kotlin.jupyter.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.debug.proxy.conversion.findFieldNameByGetterOrNull
import com.intellij.kotlin.jupyter.debug.util.findFieldByName
import com.intellij.kotlin.jupyter.debug.util.getFieldValueByName
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.evaluate.evaluationException
import java.lang.reflect.Method


/**
 * InvocationHandler which computes values by accessing fields of the underlying object reference,
 * not requiring [com.intellij.debugger.engine.evaluation.EvaluationContext] to be present.
 */
class JdiFieldAccessInvocationHandler(
    valueContext: DebugValueContext
) : JdiProxyInvocationHandler {
    override val debugProcess: DebugProcessImpl = valueContext.debugProcess
    override val objectReference: ObjectReference = valueContext.objectReference
    private val evalContext by lazy { valueContext.evaluationContext }

    override fun isApplicable(obj: Any, method: Method): Boolean {
        val isFromAnnotation = method.isAnnotationPresent(JdiFieldAccessPath::class.java)
        if (isFromAnnotation) return true
        val fieldName = method.findFieldNameByGetterOrNull() ?: return false
        return objectReference.findFieldByName(fieldName) != null
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return invokeAsFieldAccess(method)?.convertFromJdiValue(debugProcess, method.returnType, evalContext)
    }

    /**
     * Traverses the inheritance hierarchy of the object to find a field with the given name.
     */
    private fun getViaFieldAccessPath(method: Method): Value? {
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

    private fun invokeAsFieldAccess(method: Method): Value? {
        val byFieldAccessPath = getViaFieldAccessPath(method)
        if (byFieldAccessPath != null) return byFieldAccessPath

        val fieldName = method.findFieldNameByGetterOrNull()
        if (fieldName == null) {
            evaluationException("Cannot find a field with name corresponding to the getter ${method.name}")
        }
        return objectReference.getFieldValueByName(fieldName)
    }
}