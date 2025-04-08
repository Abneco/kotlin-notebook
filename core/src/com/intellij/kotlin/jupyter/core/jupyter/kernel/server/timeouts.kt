// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val KERNEL_VERIFICATION_TIMEOUT: Duration = 15.seconds
val KERNEL_KILL_WAIT_TIMEOUT: Duration = 15.seconds
