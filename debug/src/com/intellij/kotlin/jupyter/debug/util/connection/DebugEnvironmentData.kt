// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util.connection

import com.intellij.debugger.DebugEnvironment
import com.intellij.execution.runners.ExecutionEnvironment

/**
 * Data holder for debug environment configuration.
 */
internal data class DebugEnvironmentData(
    val executionEnvironment: ExecutionEnvironment,
    val debugEnvironment: DebugEnvironment
)
