package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.diagnostic.Logger

fun Logger.logList(message: String, list: List<Any>, logMethod: Logger.(String) -> Unit = Logger::warn) {
    logMethod("$message. Listing ${list.size} elements:${list.joinToString("\n", "\n")}")
}
