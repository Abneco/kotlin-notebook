// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.context

import java.lang.reflect.Type

/**
 * Represent core info about Method's return type:
 * - [runtimeReturnClass] - runtime representation of return type
 * - [genericType] - optional generic specification from this method signature
 *
 * Typically, we might have a generic type bound only present in signature,
 * and on runtime this information is lost.
 */
internal data class ReturnTypeInfo(
    val declaredReturnClass: Class<*>,
    val runtimeReturnClass: Class<*>,
    val genericType: Type? = null,
)
