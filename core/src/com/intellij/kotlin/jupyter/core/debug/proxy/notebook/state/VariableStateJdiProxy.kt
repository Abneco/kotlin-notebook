// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.VariableState

/**
 * Proxy wrapper for [VariableState]
 *
 * @see [com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler]
 */
interface VariableStateJdiProxy : VariableState, JdiObjectReferenceProxy {
    val scriptInstanceReference: ObjectReference?
    fun findVariableField(name: String): Field?
}