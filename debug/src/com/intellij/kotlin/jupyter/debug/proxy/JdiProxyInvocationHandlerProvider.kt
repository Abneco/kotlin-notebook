// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy

import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiFieldAccessInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyCompoundInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyInvocationHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.sun.jdi.ObjectReference

/**
 * Class responsible for picking the right [java.lang.reflect.InvocationHandler] for a given JDI [ObjectReference].
 *
 * This method is used to both populate [JdiProxyInvocationHandler] as well
 * as picking up a particular [JdiProxyApiDelegate] for a given [ObjectReference].
 *
 * @see [createJdiObjectProxy]
 */
interface JdiProxyInvocationHandlerProvider {
    /**
     * Suggests a list of [JdiProxyInvocationHandler]s for a [JdiProxyCompoundInvocationHandler]
     */
    fun suggestInvocationHandlersForCompoundProvider(valueContext: DebugValueContext): List<JdiProxyInvocationHandler>

    /**
     * Suggests a [JdiProxyApiDelegate] for a given [ObjectReference]
     */
    fun suggestInvocationHandlerDelegateForReference(valueContext: DebugValueContext): JdiProxyApiDelegate?

    companion object {
        private val EP: ExtensionPointName<JdiProxyInvocationHandlerProvider> = ExtensionPointName.create("com.intellij.kotlin.jupyter.debug.jdiInvocationHandlerProvider")

        fun findInvocationHandlersForCompoundProvider(valueContext: DebugValueContext): List<JdiProxyInvocationHandler> {
            val extensions = EP.extensionList
            return extensions.flatMap {
                    it.suggestInvocationHandlersForCompoundProvider(valueContext)
            }.ifEmpty {
                listOf(
                    JdiFieldAccessInvocationHandler(valueContext)
                )
            }
        }

        fun findInvocationHandlerDelegateForReference(valueContext: DebugValueContext): JdiProxyApiDelegate? {
            val extensions = EP.extensionList
            return extensions.firstNotNullOfOrNull { provider ->
                provider.suggestInvocationHandlerDelegateForReference(valueContext)
            }
        }
    }
}