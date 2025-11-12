// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.conversion

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.kotlin.jupyter.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.context.ReturnTypeInfo
import com.intellij.kotlin.jupyter.debug.proxy.createJdiObjectProxy
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.ShortValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine
import java.lang.reflect.Method
import kotlin.reflect.KFunction
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
 * Overload that accepts [ReturnTypeInfo] to reduce boilerplate at call sites.
 */
internal fun Value?.convertFromJdiValue(
    debugProcess: DebugProcessImpl,
    type: ReturnTypeInfo,
    evaluationContext: EvaluationContextImpl? = null,
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
            val returnType = type.runtimeReturnClass
            if (returnType.isInterface && returnType != ObjectReference::class.java && returnType != Any::class.java) {
                createJdiObjectProxy(
                    DebugValueContext(
                        debugProcess,
                        value,
                        evaluationContext,
                        genericType = type.genericType,
                    ),
                    returnType,
                )
            } else value
        }
        else -> null
    }
}

/**
 * Converts a property reference to its getter method name.
 * Boolean properties with 'is' prefix retain their name,
 * otherwise the 'get' prefix is added (e.g., name -> getName).
 */
internal fun KProperty<*>.toGetterName(): String {
    val propertyName = this.name
    val isBooleanProperty = returnType.classifier == Boolean::class
    return if (propertyName.startsWith("is") && propertyName.length > 2 && isBooleanProperty) {
        propertyName
    } else {
        "get${propertyName.replaceFirstChar { it.uppercaseChar() }}"
    }
}

/**
 * Tries to extract a field name from a method name, assuming it is a getter.
 * Returns null overwise.
 */
internal fun Method.findFieldNameByGetterOrNull(): String? {
    val name = name
    val isBoolean = returnType == Boolean::class.javaPrimitiveType
    return when {
        name.startsWith("is") && isBoolean -> name
        name.startsWith("get") -> {
            name.removePrefix("get").replaceFirstChar { it.lowercaseChar() }
        }
        else -> null
    }
}

internal fun KFunction<*>.findAsMethod(owner: Any): Method? {
    return owner::class.java.methods.firstOrNull { it.name == name }
}