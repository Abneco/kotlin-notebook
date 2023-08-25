// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import java.io.Closeable

fun JupyterNotebookSession.isKotlinNotebookSession(): Boolean {
    return kernelName.toLowerCaseAsciiOnly() == "kotlin"
}

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
