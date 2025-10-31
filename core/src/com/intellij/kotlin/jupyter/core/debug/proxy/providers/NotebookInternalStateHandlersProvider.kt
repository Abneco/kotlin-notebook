// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.providers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections.LinkedHashMapJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.NotebookJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.core.debug.proxy.isNotebookProxy
import com.intellij.kotlin.jupyter.core.debug.util.isOfTypeByName
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl
import java.lang.reflect.InvocationHandler

/**
 * Unified provider for all notebook-related JDI invocation handlers.
 *
 * Provides specialized handlers for:
 * - notebook object proxy
 * - variable state proxies
 * - map proxies for state traversal
 */
class NotebookInternalStateHandlersProvider : JdiProxyInvocationHandlerProvider {
    override fun suggestInvocationHandlerFor(
        process: DebugProcessImpl,
        reference: ObjectReference
    ): InvocationHandler? {
        val type = reference.referenceType()

        return when {
            type.isOfTypeByName<VariableStateImpl>() -> {
                VariableStateJdiProxyInvocationHandler(process, reference)
            }
            reference.isNotebookProxy() || type.isOfTypeByName<NotebookImpl>() -> {
                NotebookJdiProxyInvocationHandler(process, reference)
            }
            reference.isLinkedHashMap() -> {
                LinkedHashMapJdiProxyInvocationHandler(process, reference)
            }
            else -> null
        }
    }
}