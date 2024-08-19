// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.jetbrains.kotlinx.jupyter.api.AbstractKernelRunMode
import org.jetbrains.kotlinx.jupyter.api.createDefaultFilteringClassLoader

object IntellijProcessKernelRunMode : AbstractKernelRunMode("Intellij Process") {
    override fun createIntermediaryClassLoader(parent: ClassLoader) = createDefaultFilteringClassLoader(parent)

    override val shouldKillProcessOnShutdown: Boolean get() = false
    override val inMemoryOutputsSupported: Boolean get() = true
    override val isRunInsideIntellijProcess: Boolean get() = true
}
