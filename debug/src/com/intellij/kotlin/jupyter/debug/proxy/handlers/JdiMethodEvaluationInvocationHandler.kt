// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorValueOrigin
import com.intellij.kotlin.jupyter.debug.proxy.JdiMethodInvocationSignature
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.conversion.convertToJdiValue
import com.intellij.kotlin.jupyter.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread
import java.lang.reflect.Method

/**
 * InvocationHandler for evaluation of a method on a JDI [ObjectReference].
 * This requires [com.intellij.debugger.engine.evaluation.EvaluationContext] to be present for some suspension point.
 */
class JdiMethodEvaluationInvocationHandler(
    valueContext: DebugValueContext,
) : AbstractJdiInvocationHandler(valueContext) {
    private val evaluationThread: String = KotlinNotebookSessionInternalNamesProvider.notebookDebugThreadName
    override val evaluationContext: EvaluationContextImpl?
        get() = ctx.evaluationContext ?: suspendContext?.evaluationContext

    private val suspendContext
        get() = debugProcess.suspendManager.pausedContexts.firstOrNull {
            it.thread?.name() == evaluationThread
        }

    private fun findJdiMethod(refType: ReferenceType, methodName: String, paramTypes: Array<Class<Any>>): com.sun.jdi.Method? {
        val methods = refType.allMethods()
        val paramSignature = paramTypes.joinToString("") {
            DebuggerUtilsEx.typeNameToSignature(it.name)
        }

        return methods.firstOrNull { jdiMethod ->
            jdiMethod.name() == methodName && jdiMethod.signature().startsWith("($paramSignature)")
        }
    }

    override fun isApplicable(obj: Any, method: Method): Boolean {
        return evaluationContext != null
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val argumentTypes = args?.map { it.javaClass }.orEmpty().toTypedArray()
        val methodName = getMethodName(method)
        val jdiMethod = findJdiMethod(objectReference.referenceType(), methodName, argumentTypes)
        if (jdiMethod == null) {
            return null
        }

        return debugProcess.invokeInManagerThread {
            val jdiResult = invokeMethod(jdiMethod, args) ?: return@invokeInManagerThread null

            jdiResult.convertValueAndBindToDescriptor(
                method,
                methodName,
                jdiResult,
                origin = JdiDescriptorValueOrigin.MethodEvaluation,
            )
        }
    }

    private fun getMethodName(method: Method): String {
        val annotatedName = method.getAnnotation(JdiMethodInvocationSignature::class.java)?.name
        if (!annotatedName.isNullOrBlank()) {
            return annotatedName
        }

        // Using the method name yields the Java-style name
        // (e.g., getValue/isEnabled/setValue)
        return method.name
    }

    /**
     * This method requires an active breakpoint to be visible inside the IJ debugger.
     * Now, we don't have any.
     */
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
        return value.convertToJdiValue(vm, evaluationContext)
    }
}