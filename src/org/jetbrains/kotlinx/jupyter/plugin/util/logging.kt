package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("H:m:s")

fun Logger.logListWarn(message: String, list: List<Any>) {
    warn("$message. Listing ${list.size} elements:${list.joinToString("\n", "\n")}")
}

class LogEntry(
  val message: String,
  private val time: LocalTime,
  private val threadName: String,
  val stackTrace: Array<StackTraceElement>,
) {
    override fun toString(): String {
        val formattedDate = "[${time.format(dateFormatter)}]"
        val currentThread = "[$threadName]"
        return "$formattedDate $currentThread $message"
    }
}

/**
 * [LogSaver] may be used for tracking some events in debug mode
 */
class LogSaver {
    private val entries: MutableList<LogEntry> = ContainerUtil.createConcurrentList()

    fun clear() {
        entries.clear()
    }

    fun eventsAsString(): String {
        return entries.joinToString("\n")
    }

    operator fun invoke(message: String) {
        val currentThread = Thread.currentThread()
        invoke(
            LogEntry(
                message,
                LocalTime.now(),
                currentThread.name,
                currentThread.stackTrace
            )
        )
    }

    operator fun invoke(entry: LogEntry) {
        entries.add(entry)
    }
}
