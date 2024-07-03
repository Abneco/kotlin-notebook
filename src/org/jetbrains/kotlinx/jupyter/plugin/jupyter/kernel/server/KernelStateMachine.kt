// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import kotlinx.coroutines.flow.MutableStateFlow

class KernelStateMachine {
    private val state = MutableStateFlow(KernelState.STARTING)

    fun started(): Boolean {
        return state.compareAndSet(KernelState.STARTING, KernelState.STARTED)
    }

    fun terminating(): Boolean {
        started()
        return state.compareAndSet(KernelState.STARTED, KernelState.TERMINATING)
    }

    fun terminated(): Boolean {
        terminating()
        return state.compareAndSet(KernelState.TERMINATING, KernelState.TERMINATED)
    }

    val currentState get() = state.value
}