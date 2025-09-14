// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import org.jetbrains.annotations.NonNls

/**
 * Returns the value of the system property designated by [key] if it exists,
 * or sets the value returned by [valueGetter] as a system property and returns it otherwise
 *
 * This method is not thread-safe: if the property with this [key] is changed while this method is executing,
 * a result is undefined
 */
fun getOrSetSystemProperty(
    key: @NonNls String,
    valueGetter: () -> String,
): String {
    return System.getProperty(key) ?: valueGetter().also { newValue ->
        System.setProperty(key, newValue)
    }
}
