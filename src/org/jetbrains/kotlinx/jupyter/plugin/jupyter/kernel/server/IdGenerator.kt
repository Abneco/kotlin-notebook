// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.plugin.util.UniqueGenerator
import java.util.UUID

class IdGenerator : UniqueGenerator<UUID, String>() {
    override fun next(): UUID = UUID.randomUUID()

    override fun UUID.asResult(): String =
        this.toString()
}
