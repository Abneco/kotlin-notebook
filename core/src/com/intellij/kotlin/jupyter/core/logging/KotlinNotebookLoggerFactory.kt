// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.logging

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass


/**
 * Returns a Kotlin Notebook logger that corresponds to the [T] class inferred from the receiver.
 * Similar to [com.intellij.openapi.diagnostic.thisLogger]
 */
inline fun <reified T : Any> T.notebookLogger(): Logger = KotlinNotebookLoggerFactory.getInstance(T::class)

/**
 * Logger factory used by the Kotlin Notebook plugin. In production, it will just pass through logs
 * to the underlying system logger.
 *
 * During Unit or Integration tests, it can enable log tracking. In this mode, all error and warning logs
 * will be saved and can be accessed when a test completes. This allows us to verify if a test causes
 * errors that would otherwise go unnoticed. Either because they will result in a dialog being shown to
 * the user (which isn't visible in a unit test), or because they will only be shown in the
 * "IDE Internal Errors" tab, but otherwise not crash the system.
 */
object KotlinNotebookLoggerFactory {

    private val isUnitTestMode: AtomicBoolean = AtomicBoolean(false)
    private val logs: MutableList<KotlinNotebookLogger.LogEntry> = mutableListOf()

    /**
     * Enables log tracking. Should be called at the beginning of every test that
     * wants to assert the log state.
     */
    fun enableUnitTestMode() {
        isUnitTestMode.set(true)
    }

    /**
     * Disable log tracking again and clear all tracked logs.
     * Should be called at the end of every test that called [enableUnitTestMode].
     */
    fun disableUnitTestMode() {
        isUnitTestMode.set(false)
        synchronized(logs) {
            logs.clear()
        }
    }

    /**
     * Returns all saved logs (errors and warnings).
     */
    fun getTrackedLogs(): List<KotlinNotebookLogger.LogEntry> {
        return logs.toList()
    }

    /**
     * Returns a logger for a given class. Calling this multiple times for the same class
     * will return different instances, so the result of this method should generally
     * be cached.
     */
    fun getInstance(type: KClass<*>): Logger {
        val systemLogger = Logger.getInstance(type.java)
        return if (isUnitTestMode.get()) {
            KotlinNotebookLogger(systemLogger) { logEntry: KotlinNotebookLogger.LogEntry ->
                synchronized(logs) {
                    logs.add(logEntry)
                }
            }
        } else {
            systemLogger
        }
    }
}
