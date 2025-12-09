// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.kotlin.jupyter.debug.proxy.context.ReturnTypeInfo
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

internal fun Class<*>.isTopType(): Boolean =
    this == Any::class.java || this == Object::class.java

/**
 * Decision logic for:
 * - [WildcardType]: top type if there are NO lower bounds and ALL upper bounds are top types
 *   (i.e., `?` or `? extends Object`).
 * - [TypeVariable]: top type if there are no bounds or all bounds are top types
 *   (e.g., an unconstrained `<T>` with the default `Object` bound).
 */
internal fun Type.isEffectivelyTopType(): Boolean = when (this) {
    is Class<*> -> this.isTopType()
    is WildcardType -> {
        val hasLower = lowerBounds.isNotEmpty()
        val uppers = upperBounds
        !hasLower && (uppers.isEmpty() || uppers.all { it.isEffectivelyTopType() })
    }
    is TypeVariable<*> -> {
        val b = bounds
        b.isEmpty() || b.all { it.isEffectivelyTopType() }
    }
    is GenericArrayType -> false
    is ParameterizedType -> false
    else -> false
}

/**
 * Picks a more specific runtime return type for a method when its declared
 * return type is too generic (i.e., Any/Object).
 *
 * Decision rule:
 * - Refine ONLY if the declared return class is a [isTopType]
 *   AND [contextGenericType] is NOT [isEffectivelyTopType].
 */
internal fun Method.getSpecificReturnTypeIfPossible(contextGenericType: Type?): Class<*> {
    val declaredType = returnType
    if (contextGenericType == null || contextGenericType.isEffectivelyTopType()) {
        return declaredType
    }

    return if (declaredType.isTopType()) {
        contextGenericType.toRawClassOrNull() ?: declaredType
    } else {
        declaredType
    }
}

/**
 * Extracts the first type argument from a method's return type.
 * Returns it as [Type] or null if:
 *  - the return type is not parameterized,
 *  - there are no type arguments,
 *  - the argument collapses to no explicit usable bound.
 */
internal fun Method.extractFirstGenericTypeArgument(): Type? {
    val genericReturnType = genericReturnType

    if (genericReturnType !is ParameterizedType) {
        return null
    }

    val typeArguments = genericReturnType.actualTypeArguments
    if (typeArguments.isEmpty()) {
        return null
    }

    val firstTypeArgument = typeArguments[0]
    return firstTypeArgument.preferableBound()
}

private fun Type.toRawClassOrNull(): Class<*>? = when (this) {
    is Class<*> -> this
    is ParameterizedType -> rawType as? Class<*>
    is WildcardType -> {
        val wildCardBound = lowerBounds.firstOrNull() ?: upperBounds.firstOrNull()
        wildCardBound?.toRawClassOrNull()
    }
    is TypeVariable<*> -> bounds.firstOrNull()?.toRawClassOrNull()
    else -> null
}

private fun Type.preferableBound(): Type? = when (this) {
    is WildcardType -> lowerBounds.firstOrNull() ?: upperBounds.firstOrNull()
    is TypeVariable<*> -> bounds.firstOrNull()
    else -> this
}

/**
 * Resolves effective return type for the given method taking into account
 * a possible contextual element type (for collections/parameterized returns).
 *
 * - If the declared return type is generic bound, uses [contextGenericType] when provided.
 * - Extracts the first generic argument from the method, falling back to [contextGenericType].
 */
internal fun Method.resolveReturnType(contextGenericType: Type?): ReturnTypeInfo {
    val declared = returnType
    val rawElementType = extractFirstGenericTypeArgument() ?: contextGenericType
    val genericType = rawElementType?.takeUnless { it.isEffectivelyTopType() }
    val runtimeType = getSpecificReturnTypeIfPossible(genericType)

    return ReturnTypeInfo(
        declared,
        runtimeType,
        genericType = genericType,
    )
}
