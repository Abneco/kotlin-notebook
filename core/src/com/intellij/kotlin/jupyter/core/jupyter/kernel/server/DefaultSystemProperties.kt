// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

// We enforce these values of these properties
// if they're not set by the user themselves
val defaultSystemProperties: Map<String, String> = mapOf(
    "log4j2.statusLoggerLevel" to "OFF",
)
