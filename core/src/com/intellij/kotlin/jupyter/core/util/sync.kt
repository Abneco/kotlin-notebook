// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


@OptIn(ExperimentalContracts::class)
inline fun <T : Any> ReadWriteLock.tryWithWriteLock(action: () -> T): T? {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    val writeLock = writeLock()
    if (!writeLock.tryLock()) return null
    try {
        return action()
    } finally {
        writeLock.unlock()
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> ReentrantReadWriteLock.withReadLock(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return read {
        action()
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> ReentrantReadWriteLock.withWriteLock(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return write {
        action()
    }
}

