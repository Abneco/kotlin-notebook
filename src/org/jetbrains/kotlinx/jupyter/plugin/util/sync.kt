// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import kotlin.concurrent.withLock
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
inline fun <T : Any> Lock.tryWithLock(action: () -> T): T? {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (!tryLock()) return null
    try {
        return action()
    } finally {
        unlock()
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> ReadWriteLock.withReadLock(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return readLock().withLock {
        action()
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> ReadWriteLock.withWriteLock(action: () -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    return writeLock().withLock {
        action()
    }
}

