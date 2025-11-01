// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.conversion

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.createJdiObjectProxy
import com.sun.jdi.*
import kotlin.reflect.KProperty


/**
 * Converts Java object to JDI [Value].
 */
internal fun Any.convertToJdiValue(vm: VirtualMachine): Value {
    val value = this

    return when (value) {
        is Int -> vm.mirrorOf(value)
        is Long -> vm.mirrorOf(value)
        is Boolean -> vm.mirrorOf(value)
        is Byte -> vm.mirrorOf(value)
        is Short -> vm.mirrorOf(value)
        is Char -> vm.mirrorOf(value)
        is Float -> vm.mirrorOf(value)
        is Double -> vm.mirrorOf(value)
        is String -> vm.mirrorOf(value)
        is ObjectReference -> value
        is JdiObjectReferenceProxy -> value.objectReference
        else -> throw IllegalArgumentException("Unsupported argument type: ${value::class.java}")
    }
}

/**
 * Convert JDI [Value] to a Java object.
 * For [ObjectReference], creates a proxy.
 */
internal fun Value?.convertFromJdiValue(
    debugProcess: DebugProcessImpl,
    returnType: Class<*>? = null,
): Any? {
    val value = this
    if (value == null) return null

    return when (value) {
        is IntegerValue -> value.value()
        is LongValue -> value.value()
        is BooleanValue -> value.value()
        is ByteValue -> value.value()
        is ShortValue -> value.value()
        is CharValue -> value.value()
        is FloatValue -> value.value()
        is DoubleValue -> value.value()
        is StringReference -> value.value()
        is ObjectReference -> {
            // If the return type is interface, create a typed proxy
            if (returnType?.isInterface == true && returnType != ObjectReference::class.java) {
                createJdiObjectProxy(debugProcess, value, returnType)
            } else {
                value
            }
        }
        else -> null
    }
}

/**
 * Converts a property reference to its getter method name.
 */
internal fun KProperty<*>.toGetterName(): String = "get${this.name.replaceFirstChar { it.uppercaseChar() }}"