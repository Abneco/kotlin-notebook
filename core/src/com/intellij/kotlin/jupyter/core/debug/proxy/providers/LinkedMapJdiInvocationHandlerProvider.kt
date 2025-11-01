// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.providers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyInvocationHandlerProvider
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections.LinkedHashMapJdiProxyInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.isLinkedHashMap
import com.sun.jdi.ObjectReference
import java.lang.reflect.InvocationHandler

class LinkedMapJdiInvocationHandlerProvider : JdiProxyInvocationHandlerProvider {
    override fun suggestInvocationHandlerFor(
        process: DebugProcessImpl,
        reference: ObjectReference
    ): InvocationHandler? {
        return if (!reference.isLinkedHashMap()) {
            null
        } else {
            LinkedHashMapJdiProxyInvocationHandler(
                process, reference
            )
        }
    }
}