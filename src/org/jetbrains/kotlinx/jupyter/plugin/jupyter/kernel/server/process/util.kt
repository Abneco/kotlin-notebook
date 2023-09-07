// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.openapi.diagnostic.logger
import java.io.Closeable

fun rethrowAsInterrupted(e: Throwable) {
    val message = buildString {
        append("Kernel interrupted with exception: ")
        try {
            append(e.stackTraceToString())
        } catch (t: Throwable) {
            append(e::class.toString())
        }
    }
    throw InterruptedException(message)
}

internal fun Closeable.closeSafely() {
    try {
        close()
    } catch (e: Throwable) {
        logger<Closeable>().info("Close failed", e)
    }
}
