// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages

import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageType

fun interface JupyterMessageFilter {
    fun accepts(message: JupyterMessage): Boolean
}

val ACCEPT_ALL_MESSAGES = JupyterMessageFilter { true }
val DONT_ACCEPT_SHUTDOWN = JupyterMessageFilter {
    it.header.messageType != JupyterMessageType.SHUTDOWN_REQUEST
}