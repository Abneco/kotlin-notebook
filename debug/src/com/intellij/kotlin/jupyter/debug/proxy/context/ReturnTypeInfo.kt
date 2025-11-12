// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.context

/**
 * Represent core info about Method's return type:
 * - [runtimeReturnClass] - runtime representation of return type
 */
internal data class ReturnTypeInfo(
    val declaredReturnClass: Class<*>,
    val runtimeReturnClass: Class<*>,
    val genericType: Class<*>? = null,
)
