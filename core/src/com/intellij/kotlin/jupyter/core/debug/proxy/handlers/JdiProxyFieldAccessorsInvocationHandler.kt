// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.BaseJdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.core.debug.session.names.KotlinNotebookSessionInternalNamesProvider
import com.sun.jdi.ObjectReference
import java.lang.reflect.Method

/**
 * InvocationHandler which computes values by accessing fields of the underlying object reference,
 * not requiring [com.intellij.debugger.engine.evaluation.EvaluationContext] to be present.
 */
internal open class JdiProxyFieldAccessorsInvocationHandler(
    debugProcess: DebugProcessImpl,
    objectReference: ObjectReference,
    apiExtensionHandler: JdiProxyApiExtension = BaseJdiProxyApiExtension(objectReference),
) : JdiProxyInvocationHandlerBase(debugProcess, objectReference, apiExtensionHandler) {
    private val evaluationThread: String = KotlinNotebookSessionInternalNamesProvider.notebookDebugThreadName

    protected val suspendContext get() = debugProcess.suspendManager.pausedContexts.firstOrNull {
        it.thread?.name() == evaluationThread
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        val declaringClass = method.declaringClass
        val handler = extensionHandler
        if (handler != null && declaringClass.isInstance(handler)) {
            return method.invoke(handler, *(args ?: emptyArray()))
        }

        return invokeRemoteMethod(method, args)
    }

    private fun invokeRemoteMethod(method: Method, args: Array<out Any>?): Any? {
        // Try invoking the method as field access
        return invokeAsFieldAccess(method).convertFromJdiValue(debugProcess, method.returnType)
    }
}