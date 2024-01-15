@file:Suppress("UNUSED")

package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments


fun Logger.logListInfo(message: String, list: List<Any>) {
    info("$message. Listing ${list.size} elements:${list.joinToString("\n", "\n")}")
}

fun Logger.doUnderDebug(action: Logger.() -> Unit) {
    if (isDebugEnabled) {
        action()
    }
}

fun Logger.errorUnderDebug(message: String) = doUnderDebug {
    error(message)
}

fun Logger.errorUnderDebug(message: String, throwable: Throwable) = doUnderDebug {
    error(message, throwable)
}


fun Logger.errorUnderDebug(message: String, vararg attachments: Attachment) = doUnderDebug {
    errorWithAttachments(message, *attachments)
}

fun Logger.errorUnderDebug(throwable: Throwable) = doUnderDebug {
    error(throwable)
}

fun Logger.errorWithAttachments(message: String, vararg attachments: Attachment) =
    error(
        message,
        RuntimeExceptionWithAttachments(message, *attachments)
    )

fun Logger.warnInTests(messageFactory: () -> String) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
        warn(messageFactory())
    }
}
