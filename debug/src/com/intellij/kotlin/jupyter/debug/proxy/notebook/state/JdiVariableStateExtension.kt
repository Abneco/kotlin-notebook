// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook.state

import com.intellij.debugger.collections.visualizer.core.backend.XCollectionAccessor
import com.intellij.debugger.engine.JavaValue
import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference

/**
 * Extension for [org.jetbrains.kotlinx.jupyter.api.VariableState] remote object proxy.
 */
interface JdiVariableStateExtension : JdiProxyApiDelegate {
    /**
     * Rendered text representation from the runtime context.
     */
    val renderedText: String?

    /**
     * ObjectReference for the variable's value from the script instance.
     */
    val variableValueObjectReference: ObjectReference?

    /**
     * Updates this proxy with debugger-manager backed [JavaValue]
     */
    fun updateFromRuntimeContext(javaValue: JavaValue?)

    fun findVariableField(name: String): Field?

    /**
     * Tries to find a collection accessor for this variable.
     * If it's not a collection or is of an unsupported type returns null.
     */
    suspend fun findAccessor(): XCollectionAccessor?
}