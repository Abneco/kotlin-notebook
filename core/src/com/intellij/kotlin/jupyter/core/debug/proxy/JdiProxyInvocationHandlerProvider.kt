// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.sun.jdi.ObjectReference
import java.lang.reflect.InvocationHandler

/**
 * Class responsible for picking the right [java.lang.reflect.InvocationHandler] for a given JDI [ObjectReference].
 * In case if no specific provider for a [ObjectReference] is found,
 * a default [JdiProxyFieldAccessorsInvocationHandler] is used.
 *
 * @see [createJdiObjectProxy]
 */
fun interface JdiProxyInvocationHandlerProvider {
    fun suggestInvocationHandlerFor(process: DebugProcessImpl, reference: ObjectReference): InvocationHandler?

    companion object {
        private val EP: ExtensionPointName<JdiProxyInvocationHandlerProvider> = ExtensionPointName.Companion.create("com.intellij.kotlin.jupyter.debug.jdiInvocationHandlerProvider")

        fun findInvocationHandlerForReference(process: DebugProcessImpl, reference: ObjectReference): InvocationHandler {
            val extensions = EP.extensionList
            // find specific or fallback to a default one
            return extensions.firstNotNullOfOrNull { provider ->
                provider.suggestInvocationHandlerFor(process, reference)
            } ?: JdiProxyFieldAccessorsInvocationHandler(process, reference)
        }
    }
}