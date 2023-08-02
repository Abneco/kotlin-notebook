// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.concurrency.ConcurrentCollectionFactory
import java.util.UUID

class IdGenerator {
    private val ids = ConcurrentCollectionFactory.createConcurrentSet<UUID>()

    fun generate(): String {
        while (true) {
            val uuid = UUID.randomUUID()
            if(ids.add(uuid)) return uuid.toString()
        }
    }
}
