// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import org.slf4j.event.Level

enum class LogLevel(private val intLevel: Int) {
    OFF(Level.ERROR.toInt() + 10),
    ERROR(Level.ERROR.toInt()),
    WARN(Level.WARN.toInt()),
    INFO(Level.INFO.toInt()),
    DEBUG(Level.DEBUG.toInt()),
    TRACE(Level.TRACE.toInt());

    fun toInt() = intLevel
}