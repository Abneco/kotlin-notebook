// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import java.util.concurrent.atomic.AtomicBoolean

class PassOnceGuard<KeyT>: Disposable {
    private val guards = ConcurrentCollectionFactory.createConcurrentMap<KeyT, AtomicBoolean>()

    fun alreadyEntered(key: KeyT): Boolean {
        val guard = guards.getOrPut(key) { AtomicBoolean(false) }
        return !guard.compareAndSet(false, true)
    }

    override fun dispose() {
        guards.clear()
    }
}
