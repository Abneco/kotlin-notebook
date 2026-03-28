// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Launches a coroutine for each of the underlying elements and collects the results
 */
suspend fun <T, R> Collection<T>.parallelMap(transform: suspend (T) -> R?): List<R?> = coroutineScope {
    map { element ->
        async {
            transform(element)
        }
    }.awaitAll()
}