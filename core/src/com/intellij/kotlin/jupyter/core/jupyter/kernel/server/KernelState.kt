// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

enum class KernelState {
    STARTED_UNVERIFIED,
    STARTED_VERIFIED,
    TERMINATING,
    TERMINATED,
}

val KernelState.canBeStopped: Boolean get() = equals(KernelState.STARTED_UNVERIFIED) || equals(KernelState.STARTED_VERIFIED)