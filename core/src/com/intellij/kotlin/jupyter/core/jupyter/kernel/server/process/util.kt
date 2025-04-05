// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import java.io.Closeable

fun rethrowAsInterrupted(e: Throwable) {
    val message = buildString {
        append("Kernel interrupted with exception: ")
        try {
            append(e.stackTraceToString())
        } catch (_: Throwable) {
            append(e::class.toString())
        }
    }
    throw InterruptedException(message)
}

fun Closeable.closeSafely() {
    try {
        close()
    } catch (e: Throwable) {
        notebookLogger<Closeable>().info("Close failed", e)
    }
}
