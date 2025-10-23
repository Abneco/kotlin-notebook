// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.providers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.util.isOfTypeByName
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.VariableStateImpl
import java.lang.reflect.InvocationHandler

class VariableStateJdiHandlerProvider : JdiProxyInvocationHandlerProvider {
    override fun suggestInvocationHandlerFor(
        process: DebugProcessImpl,
        reference: ObjectReference
    ): InvocationHandler? {
        if (!reference.isOfTypeByName<VariableStateImpl>()) {
            return null
        }

        return VariableStateJdiProxyInvocationHandler(process, reference)
    }
}