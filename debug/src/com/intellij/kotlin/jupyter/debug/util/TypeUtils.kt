// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.kotlin.jupyter.debug.proxy.context.ReturnTypeInfo
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal fun Class<*>.isTopType(): Boolean =
    this == Any::class.java || this == Object::class.java

/**
 * Picks a more specific runtime return type for a method when its declared
 * return type is too generic (i.e., Any/Object).
 * If possible, falls back to the provided [contextGenericType]
 */
internal fun Method.getSpecificReturnTypeIfPossible(contextGenericType: Class<*>?): Class<*> {
    val declaredType = returnType
    if (contextGenericType == null) {
        return declaredType
    }

    return if (declaredType.isTopType()) {
        contextGenericType
    } else {
        declaredType
    }
}

/**
 * Extracts the first generic type argument from a method's return type.
 * Returns it as a Class, or null if:
 *  - The return type is not parameterized
 *  - There are no type arguments
 *  - The type argument is not a Class (e.g., wildcard, type variable)
 */
internal fun Method.extractFirstGenericTypeArgument(): Class<*>? {
    val genericReturnType = genericReturnType

    if (genericReturnType !is ParameterizedType) {
        return null
    }

    val typeArguments = genericReturnType.actualTypeArguments
    if (typeArguments.isEmpty()) {
        return null
    }

    val firstTypeArgument = typeArguments[0]
    return firstTypeArgument.toClassOrNull()
}

/**
 * Converts a Type to Class if possible.
 */
private fun Type.toClassOrNull(): Class<*>? {
    return when (this) {
        is Class<*> -> this
        is ParameterizedType -> rawType as? Class<*>
        else -> null
    }
}

/**
 * Resolves effective return type for the given method taking into account
 * a possible contextual element type (for collections/parameterized returns).
 *
 * - If the declared return type is Any/Object, uses [contextGenericType] when provided.
 * - Extracts the first generic argument from the method, falling back to [contextGenericType].
 */
internal fun Method.resolveReturnType(contextGenericType: Class<*>?): ReturnTypeInfo {
    val declared = returnType
    val genericType = extractFirstGenericTypeArgument() ?: contextGenericType
    val runtime = getSpecificReturnTypeIfPossible(genericType)
    return ReturnTypeInfo(
        declared,
        runtime,
        genericType = genericType,
    )
}
