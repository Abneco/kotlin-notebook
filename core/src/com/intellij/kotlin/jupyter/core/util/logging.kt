@file:Suppress("UNUSED")

package com.intellij.kotlin.jupyter.core.util

import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments


private class KotlinNotebookLogging

val kotlinNotebookLogger: Logger get() = KotlinNotebookLoggerFactory.getInstance(KotlinNotebookLogging::class)

fun Logger.logListInfo(message: String, list: List<Any>) {
    info("$message. Listing ${list.size} elements:${list.joinToString("\n", "\n")}")
}

fun Logger.warnUnderDebug(message: String): Unit = doUnderDebug {
    warn(message)
}

fun Logger.errorUnderDebug(message: String): Unit = doUnderDebug {
    error(message)
}

fun Logger.errorUnderDebug(message: String, throwable: Throwable): Unit = doUnderDebug {
    error(message, throwable)
}

fun Logger.errorUnderDebug(message: String, vararg attachments: Attachment): Unit = doUnderDebug {
    errorWithAttachments(message, *attachments)
}

fun Logger.errorUnderDebug(throwable: Throwable): Unit = doUnderDebug {
    error(throwable)
}

fun Logger.debugWithAttachments(message: () -> String, attachments: () -> List<Attachment>) {
    if (!isDebugEnabled) return
    val messageValue = message()
    debug(
        messageValue,
        RuntimeExceptionWithAttachments(messageValue, *attachments().toTypedArray())
    )
}

fun Logger.errorWithAttachments(message: String, vararg attachments: Attachment): Unit =
    error(
        message,
        RuntimeExceptionWithAttachments(message, *attachments)
    )

fun Logger.warnInTests(messageFactory: () -> String) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
        warn(messageFactory())
    }
}

fun Logger.reportErrorTestAware(message: String, attachment: Attachment) {
    val logReference = if (ApplicationManager.getApplication().isUnitTestMode)
        ::errorWithAttachments
    else
        ::errorUnderDebug

    logReference(message, arrayOf(attachment))
}

private fun Logger.doUnderDebug(action: Logger.() -> Unit) {
    if (isDebugEnabled) {
        action()
    }
}
