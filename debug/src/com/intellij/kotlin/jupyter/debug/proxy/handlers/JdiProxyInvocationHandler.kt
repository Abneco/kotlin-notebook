// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.sun.jdi.ObjectReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * Base interface for [InvocationHandler] implementations
 * that delegate to a JDI [ObjectReference].
 *
 * @see [JdiProxyCompoundInvocationHandler]
 */
interface JdiProxyInvocationHandler : InvocationHandler {
    fun isApplicable(obj: Any, method: Method): Boolean

    val debugProcess: DebugProcessImpl

    val objectReference: ObjectReference
}