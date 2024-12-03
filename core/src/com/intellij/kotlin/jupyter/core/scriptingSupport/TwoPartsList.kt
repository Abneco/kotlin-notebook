// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.util.withReadLock
import com.intellij.kotlin.jupyter.core.util.withWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

internal class TwoPartsList<T>(
    private val initialPart: MutableSet<T> = mutableSetOf(),
    private val snippetsPart: MutableSet<T> = mutableSetOf(),
) {
    private val lock = ReentrantReadWriteLock()

    val size: Int get() = initialPart.size + snippetsPart.size

    val hasInitialPart: Boolean get() = initialPart.isNotEmpty()

    fun clear() {
        lock.withWriteLock { snippetsPart.clear() }
    }

    fun addInitial(items: Collection<T>) {
        lock.withWriteLock { initialPart.addAll(items) }
    }

    fun addSnippet(items: Collection<T>) {
        lock.withWriteLock { snippetsPart.addAll(items) }
    }

    fun removeSnippet(items: Collection<T>) {
        lock.withWriteLock { snippetsPart.removeAll(items) }
    }

    fun getList(): List<T> {
        return lock.withReadLock { (initialPart + snippetsPart).distinct() }
    }
}
