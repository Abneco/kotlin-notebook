// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.util.connection.isVMDisconnectedException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method


/**
 * Base [InvocationHandler] which delegates invocation to a list of [JdiProxyInvocationHandler],
 * picking the first applicable one.
 *
 * @see [JdiProxyInvocationHandlerProvider]
 */
class JdiProxyCompoundInvocationHandler(
    valueContext: DebugValueContext
) : InvocationHandler {
    private val invocationHandlers: List<JdiProxyInvocationHandler> = JdiProxyInvocationHandlerProvider.findInvocationHandlersForCompoundProvider(valueContext)

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val handlerDelegate = invocationHandlers.firstOrNull { it.isApplicable(proxy, method) }
        return try {
            handlerDelegate?.invoke(proxy, method, args)
        } catch (e: Throwable) {
            if (e.isVMDisconnectedException) {
                null
            } else {
                throw e
            }
        }
    }
}