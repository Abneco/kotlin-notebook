// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook.state

import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorAwareProxy
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Extension for [org.jetbrains.kotlinx.jupyter.api.VariableState] remote object proxy.
 */
interface JdiVariableStateExtension : JdiDescriptorAwareProxy {
    /**
     * Actual variable's value from the script instance.
     * It might not be of [ObjectReference] type, e.g., some primitive type
     */
    val variableValue: Value?

    fun findVariableField(name: String): Field?

    fun <T: Any> createProxyForValue(proxyType: Class<T>): T?
}