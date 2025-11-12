// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook.state

import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorAwareProxy
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference

/**
 * Extension for [org.jetbrains.kotlinx.jupyter.api.VariableState] remote object proxy.
 */
interface JdiVariableStateExtension : JdiDescriptorAwareProxy {
    /**
     * ObjectReference for the variable's value from the script instance.
     */
    val variableValueObjectReference: ObjectReference?

    fun findVariableField(name: String): Field?

    fun <T: Any> createProxyForValue(proxyType: Class<T>): T?
}