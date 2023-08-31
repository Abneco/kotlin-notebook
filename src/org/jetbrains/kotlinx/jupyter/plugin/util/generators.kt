// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.concurrency.ConcurrentCollectionFactory

abstract class UniqueGenerator<V : Any, R> {
    protected val generated: MutableSet<V> = ConcurrentCollectionFactory.createConcurrentSet()

    abstract fun next(): V

    abstract fun V.asResult(): R

    fun generate(): R {
        while (true) {
            val nextItem = next()
            if (generated.add(nextItem)) return nextItem.asResult()
        }
    }
}