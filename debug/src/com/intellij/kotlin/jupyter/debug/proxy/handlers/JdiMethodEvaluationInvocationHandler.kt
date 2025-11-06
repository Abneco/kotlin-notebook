// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.JdiMethodInvocationSignature
import com.intellij.kotlin.jupyter.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.debug.proxy.conversion.convertToJdiValue
import com.intellij.kotlin.jupyter.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.lang.reflect.Method

/**
 * InvocationHandler for evaluation of a method on a JDI [ObjectReference].
 * This requires [com.intellij.debugger.engine.evaluation.EvaluationContext] to be present for some suspension point.
 */
class JdiMethodEvaluationInvocationHandler(
    valueContext: DebugValueContext,
) : JdiProxyInvocationHandler {
    private val evaluationThread: String = KotlinNotebookSessionInternalNamesProvider.notebookDebugThreadName
    private val breakpointContext = valueContext.evaluationContext
    private val evaluationContext
        get() = breakpointContext ?: suspendContext?.evaluationContext

    private val suspendContext
        get() = debugProcess.suspendManager.pausedContexts.firstOrNull {
            it.thread?.name() == evaluationThread
        }

    private fun findJdiMethod(refType: ReferenceType, methodName: String, paramTypes: Array<Class<Any>>): com.sun.jdi.Method? {
        val methods = refType.allMethods()

        return methods.firstOrNull { jdiMethod ->
            jdiMethod.name() == methodName && jdiMethod.argumentTypes().size == paramTypes.size
        }
    }

    override val debugProcess: DebugProcessImpl = valueContext.debugProcess

    override val objectReference: ObjectReference = valueContext.objectReference

    override fun isApplicable(obj: Any, method: Method): Boolean {
        // until it's stable
        return suspendContext?.evaluationContext != null
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val argumentTypes = args?.map { it.javaClass }.orEmpty().toTypedArray()
        val methodName = getMethodName(method)
        val jdiMethod = findJdiMethod(objectReference.referenceType(), methodName, argumentTypes)
        if (jdiMethod == null) {
            return null
        }

        return invokeMethod(jdiMethod, args)
            ?.convertFromJdiValue(
                debugProcess,
                method.returnType,
                evaluationContext = evaluationContext,
            )
    }

    private fun getMethodName(method: Method): String {
        val isAnnotated = method.getAnnotation(JdiMethodInvocationSignature::class.java)?.name
        return isAnnotated ?: method.name
    }

    /**
     * This method requires an active breakpoint to be visible inside the IJ debugger.
     * Now, we don't have any.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNUSED")
    private fun invokeMethod(jdiMethod: com.sun.jdi.Method, args: Array<out Any>?): Value? {
        // Convert to JDI
        val jdiArgs = args?.map { convertToJdiValue(it) } ?: emptyList()

        // Invoke method on remote object
        val evaluationContext = evaluationContext ?: return null
        val result = debugProcess.invokeMethod(
            evaluationContext,
            objectReference,
            jdiMethod,
            jdiArgs
        )

        return result
    }

    private fun convertToJdiValue(value: Any): Value {
        val vm = objectReference.virtualMachine()
        return value.convertToJdiValue(vm)
    }
}