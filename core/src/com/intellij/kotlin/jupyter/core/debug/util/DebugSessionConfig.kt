// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.util

/**
 * Configuration for creating a debug session.
 * [silent] If true, the session will be created without showing UI notifications
 */
internal data class DebugSessionConfig(
    val port: Int,
    val transport: Int = 0,
    val isLocal: Boolean = true,
    val silent: Boolean = true
)

