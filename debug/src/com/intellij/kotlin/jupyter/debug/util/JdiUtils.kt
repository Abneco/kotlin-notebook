// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.debugger.engine.DebuggerUtils
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

/**
 * Computes the value of the [Field] with the specified name for this [ObjectReference].
 *
 * Note that method may throw [IllegalArgumentException].
 */
internal fun ObjectReference.getFieldValueByName(name: String): Value? {
    val fieldReference = findFieldByName(name) ?: return null
    return getValue(fieldReference)
}

internal fun ObjectReference.findFieldByName(name: String): Field? {
    return referenceType().findFieldByName(name)
}

internal fun ReferenceType.findFieldByName(name: String): Field? {
    return DebuggerUtils.findField(this, name)
}

internal inline fun <reified T : Any> ReferenceType.isOfTypeByName(): Boolean {
    return name() == T::class.java.name
}

internal inline fun <reified T : Any> ObjectReference.isOfTypeByName(): Boolean {
    return referenceType().isOfTypeByName<T>()
}