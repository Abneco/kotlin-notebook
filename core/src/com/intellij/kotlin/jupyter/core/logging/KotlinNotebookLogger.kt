// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.logging

import com.intellij.openapi.diagnostic.DelegatingLogger
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger


/**
 * Logger, designed for use in the Kotlin Notebook Plugin. It makes it possible to track errors and warnings logged
 * during execution and then later examine them through [KotlinNotebookLoggerFactory.getTrackedLogs]
 */
class KotlinNotebookLogger(
    delegate: Logger,
    private val addLogFunc: ((LogEntry) -> Unit)? = null
): DelegatingLogger<Logger>(delegate) {

    // Simple container wrapping all the log input
    class LogEntry(val level: LogLevel, val message: String?, val t: Throwable?, val details: Array<out String>)

    override fun error(message: String?, t: Throwable?, vararg details: String) {
        addLogFunc?.let { it(LogEntry(LogLevel.ERROR, message, t, details)) }
        super.error(message, t, *details)
    }

    override fun warn(message: String?, t: Throwable?) {
        addLogFunc?.let { it(LogEntry(LogLevel.WARNING, message, t, emptyArray())) }
        super.warn(message, t)
    }
}