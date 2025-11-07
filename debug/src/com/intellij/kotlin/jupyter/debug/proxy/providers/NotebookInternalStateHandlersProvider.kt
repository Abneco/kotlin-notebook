// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.providers

import com.intellij.kotlin.jupyter.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate
import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiFieldAccessInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiMethodEvaluationInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyDelegatingInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.JdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.JdiMapDelegateExtensionHandlerImpl
import com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.JdiNotebookDelegateHandler
import com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.state.JdiVariableStateDelegateHandler
import com.intellij.kotlin.jupyter.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.debug.proxy.isNotebookProxy
import com.intellij.kotlin.jupyter.debug.util.isOfTypeByName
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl

/**
 * Unified provider for all notebook-related JDI invocation handlers.
 *
 * Provides specialized handlers for:
 * - notebook object proxy
 * - variable state proxies
 * - map proxies for state traversal
 */
class NotebookInternalStateHandlersProvider : JdiProxyInvocationHandlerProvider {
    override fun suggestInvocationHandlersForCompoundProvider(valueContext: DebugValueContext): List<JdiProxyInvocationHandler> {
        return listOf(
            JdiFieldAccessInvocationHandler(valueContext),
            JdiProxyDelegatingInvocationHandler(valueContext),
            JdiMethodEvaluationInvocationHandler(valueContext)
        )
    }

    override fun suggestInvocationHandlerDelegateForReference(
        valueContext: DebugValueContext
    ): JdiProxyApiDelegate? {
        val reference = valueContext.objectReference
        val type = reference.referenceType()

        return when {
            type.isOfTypeByName<VariableStateImpl>() -> {
                JdiVariableStateDelegateHandler(valueContext)
            }
            reference.isNotebookProxy() || type.isOfTypeByName<NotebookImpl>() -> {
                JdiNotebookDelegateHandler(valueContext)
            }
            reference.isLinkedHashMap() -> {
                JdiMapDelegateExtensionHandlerImpl(valueContext)
            }
            else -> null
        }
    }
}