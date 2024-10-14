// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import java.util.concurrent.atomic.AtomicReference

/**
 * Class to manage the state of a Kotlin runnable handler (kernel),
 * independently of its [KotlinNotebookSessionRunMode],
 * using a finite state machine approach.
 *
 * It's guaranteed that the kernel states are always changed in the following order:
 * [KernelState.STARTING] -> [KernelState.STARTED] ->
 * [KernelState.TERMINATING] -> [KernelState.TERMINATED]
 *
 * This class is thread-safe.
 */
class KernelStateMachine {
    private val state = AtomicReference(KernelState.STARTING)

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

    val currentState: KernelState get() = state.get()
}