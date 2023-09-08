// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import java.util.*

interface KotlinKernelListener : EventListener {
    fun kernelTerminated(event: KotlinKernelEvent) {}
}

interface KotlinKernelEvent {
    val source: KotlinKernelRunnableHandler
}
