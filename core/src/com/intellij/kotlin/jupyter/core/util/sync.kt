// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


internal inline fun <T> withReadAccess(crossinline block: () -> T): T {
    return if (ApplicationManager.getApplication().isDispatchThread || ApplicationManager.getApplication().isReadAccessAllowed) {
        block()
    } else ReadAction.compute<T, Throwable> {
        block()
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

