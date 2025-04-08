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
 * [KernelState.STARTED_UNVERIFIED] -> [KernelState.STARTED_VERIFIED] ->
 * [KernelState.TERMINATING] -> [KernelState.TERMINATED]
 *
 * This class is thread-safe.
 */
class KernelStateMachine {
    private val state = AtomicReference(KernelState.STARTED_UNVERIFIED)

    fun verified(): Boolean {
        return state.compareAndSet(KernelState.STARTED_UNVERIFIED, KernelState.STARTED_VERIFIED)
    }

    fun terminating(): Boolean {
        verified()
        return state.compareAndSet(KernelState.STARTED_VERIFIED, KernelState.TERMINATING)
    }

    fun terminated(): Boolean {
        terminating()
        return state.compareAndSet(KernelState.TERMINATING, KernelState.TERMINATED)
    }

    val currentState: KernelState get() = state.get()
}