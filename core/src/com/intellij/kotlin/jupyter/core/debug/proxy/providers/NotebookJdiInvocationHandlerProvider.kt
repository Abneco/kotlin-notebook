// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.providers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.core.debug.proxy.isNotebookProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.NotebookJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.util.isOfTypeByName
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl
import java.lang.reflect.InvocationHandler

class NotebookJdiInvocationHandlerProvider : JdiProxyInvocationHandlerProvider {
    override fun suggestInvocationHandlerFor(
        process: DebugProcessImpl,
        reference: ObjectReference
    ): InvocationHandler? {
        val isNotebookType = reference.isNotebookProxy() || reference.isOfTypeByName<NotebookImpl>()
        if (!isNotebookType) {
            return null
        }

        return NotebookJdiProxyInvocationHandler(process, reference)
    }
}