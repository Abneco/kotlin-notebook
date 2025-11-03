// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiDelegate
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyInvocationHandlerProvider
import com.sun.jdi.ObjectReference
import java.lang.reflect.Method

/**
 * Handler which delegates all method calls to a specific [extensionDelegate].
 *
 * [extensionDelegate] adapts all API extensions of [JdiProxyApiDelegate] classes
 */
class JdiProxyDelegatingInvocationHandler(
    valueContext: DebugValueContext
) : JdiProxyInvocationHandler {
    override val debugProcess: DebugProcessImpl = valueContext.debugProcess
    override val objectReference: ObjectReference = valueContext.objectReference

    private val extensionDelegate = JdiProxyInvocationHandlerProvider.findInvocationHandlerDelegateForReference(valueContext)

    override fun isApplicable(obj: Any, method: Method): Boolean {
        val declaringClass = method.declaringClass
        return declaringClass.isInstance(extensionDelegate)
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return method.invoke(extensionDelegate, *(args ?: emptyArray()))
    }
}