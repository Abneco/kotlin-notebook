// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiFieldAccessPath
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.VariableState

/**
 * Proxy wrapper for [VariableState]
 *
 * @see [com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.VariableStateJdiProxyInvocationHandler]
 */
interface VariableStateJdiProxy : JdiVariableStateExtension, VariableState {
    /**
     * Reference to the script instance that contains this variable.
     */
    @get:JdiFieldAccessPath("scriptInstance")
    val scriptInstanceReference: ObjectReference?
}