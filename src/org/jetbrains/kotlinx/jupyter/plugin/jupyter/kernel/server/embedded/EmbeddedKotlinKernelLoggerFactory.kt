// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowRunMode
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger
import org.slf4j.helpers.MessageFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Class responsible for creating loggers when using the embedded kernel.
 *
 * This allows us to intercept kernel log messages and redirect them to the console window,
 * but this will only work if the `consoleView` has been set. Since there is a circular
 * dependency between this factory and the console view, the console view needs
 * to register itself when created. See [KotlinNotebookToolWindowRunMode] for more details.
 */
class EmbeddedKotlinKernelLoggerFactory: KernelLoggerFactory {
    // Reference to the ConsoleView showing the logs. If this is `null`
    // log entries will be ignored.
    var consoleView: ConsoleView? = null

    // Cache all existing logger instances, so we hand out the same instance
    // to all calls for the same clazz or category.
    private val loggerMap = ConcurrentHashMap<String, Logger>()

    override fun getLogger(clazz: Class<*>): Logger = getLoggerInstance(clazz.name)
    override fun getLogger(category: String): Logger = getLoggerInstance(category)

    private fun getLoggerInstance(name: String): Logger {
        val logger: Logger? = loggerMap[name]
        if (logger != null) {
            return logger
        } else {
            val newLogger = EmbeddedKotlinKernelLogger(this, name)
            val oldLogger: Logger? = loggerMap.putIfAbsent(name, newLogger)
            return oldLogger ?: newLogger
        }
    }

    /**
     * Logger class for the embedded kernel. This implementation will redirect all
     * log messages to the [ConsoleView] defined in [EmbeddedKotlinKernelLoggerFactory.consoleView].
     *
     * If no console view is present, log entries will be ignored.
     */
    class EmbeddedKotlinKernelLogger(
        private val factory: EmbeddedKotlinKernelLoggerFactory,
        private val tag: String? = null,
        private val logLevel: Level = Level.DEBUG
    ): AbstractLogger() {

        companion object {
            val NEW_LINE: String = System.lineSeparator()
            val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS").withZone(ZoneId.systemDefault())
        }

        override fun isTraceEnabled(): Boolean = isLogLevelEnabled(Level.TRACE)
        override fun isTraceEnabled(marker: Marker?): Boolean = isTraceEnabled()
        override fun isDebugEnabled(): Boolean = isLogLevelEnabled(Level.DEBUG)
        override fun isDebugEnabled(marker: Marker?): Boolean = isDebugEnabled()
        override fun isInfoEnabled(): Boolean = isLogLevelEnabled(Level.INFO)
        override fun isInfoEnabled(marker: Marker?): Boolean = isInfoEnabled()
        override fun isWarnEnabled(): Boolean = isLogLevelEnabled(Level.WARN)
        override fun isWarnEnabled(marker: Marker?): Boolean = isWarnEnabled()
        override fun isErrorEnabled(): Boolean = isLogLevelEnabled(Level.ERROR)
        override fun isErrorEnabled(marker: Marker?): Boolean = isErrorEnabled()

        private fun isLogLevelEnabled(level: Level): Boolean {
            return logLevel.toInt() <= level.toInt()
        }

        override fun getFullyQualifiedCallerName(): String {
            return "" // Not used
        }

        override fun handleNormalizedLoggingCall(
            level: Level?,
            marker: Marker?,
            messagePattern: String?,
            arguments: Array<out Any>?,
            throwable: Throwable?
        ) {
            val console: ConsoleView = factory.consoleView ?: return

            val type = when(level) {
                Level.ERROR -> ConsoleViewContentType.ERROR_OUTPUT
                Level.WARN -> ConsoleViewContentType.LOG_WARNING_OUTPUT
                Level.INFO -> ConsoleViewContentType.LOG_INFO_OUTPUT
                Level.DEBUG -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
                Level.TRACE -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT
                null -> ConsoleViewContentType.NORMAL_OUTPUT
            }
            val timestamp = TIMESTAMP_FORMATTER.format(Instant.now())
            val log = StringBuilder(timestamp)
            log.append(' ')
            if (tag != null) {
                log.append(tag)
                log.append(' ')
            }
            if (marker != null) {
                log.append("[${marker.name}] ")
            }
            if (level != null) {
                log.append(level.name)
                log.append(' ')
            }
            if (messagePattern != null) {
                log.append(MessageFormatter.basicArrayFormat(messagePattern, arguments))
            }
            if (throwable != null) {
                // If we also printed a standard message, we want some distinction between that and any error,
                // so add a newline to separate them.
                if (messagePattern != null) {
                    log.append(NEW_LINE)
                }
                log.append(throwable.stackTraceToString())
            }
            // Some log messages do not have a newline, which can result in multiple log entries being show
            // on the same line in ConsoleView. To avoid this, we manually add a newline between entries
            // if needed.
            if (!log.endsWith(NEW_LINE)) {
                log.append(NEW_LINE)
            }
            console.print(log.toString(), type)
        }
    }
}
