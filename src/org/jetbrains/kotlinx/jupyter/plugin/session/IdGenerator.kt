// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

class IdGenerator {
    val ids: Set<UUID> = mutableSetOf()
    val lock = ReentrantLock()

    fun generate(): String {
        lock.lock()
        var uid: UUID
        do {
          uid = UUID.randomUUID()
        } while(uid in ids)
        lock.unlock()
        return uid.toString()
    }
}
