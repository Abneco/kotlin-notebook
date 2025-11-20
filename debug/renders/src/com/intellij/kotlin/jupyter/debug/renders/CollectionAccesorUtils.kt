// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.renders

import com.intellij.debugger.collections.visualizer.core.CollectionElement
import com.intellij.debugger.collections.visualizer.core.backend.XCollectionAccessor
import com.intellij.debugger.collections.visualizer.core.backend.XCollectionAccessorProvider
import com.intellij.debugger.engine.JavaValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal const val COLLECTION_MAX_VIEW_SIZE = 300L

suspend fun JavaValue.findCollectionAccessor(): XCollectionAccessor? {
    return XCollectionAccessorProvider.findAccessor(this)
}

suspend fun XCollectionAccessor.getSize(): Long? {
    return loadMetadata()?.collectionSize
}

suspend fun XCollectionAccessor.getValues(unlimited: Boolean = false): Flow<CollectionElement> {
    val size = loadMetadata()?.collectionSize
    if (size == null) {
        return emptyFlow()
    }

    val viewSize = if (unlimited) {
        size
    } else {
        COLLECTION_MAX_VIEW_SIZE.coerceAtMost(size)
    }.toInt()

    return subCollection(0, viewSize)
}