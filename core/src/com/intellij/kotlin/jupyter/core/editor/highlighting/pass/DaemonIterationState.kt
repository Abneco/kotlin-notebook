// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.pass

import java.util.concurrent.atomic.AtomicReference

internal enum class DaemonState {
    IDLE,
    SETUP,
    IN_PROGRESS;

    val isIdle: Boolean get() = this == IDLE
    val isInProgress: Boolean get() =this == IN_PROGRESS
}

internal class DaemonIterationState {
    private val state = AtomicReference(DaemonState.IDLE)

    fun get(): DaemonState = state.get()

    val isIdle: Boolean get() = get().isIdle
    val isInProgress: Boolean get() = get().isInProgress

    fun enterSetupPhase(): Boolean {
        return state.compareAndSet(DaemonState.IDLE, DaemonState.SETUP)
    }

    fun enterProgressPhase(): Boolean {
        return state.compareAndSet(DaemonState.SETUP, DaemonState.IN_PROGRESS)
    }

    fun setIdle() {
        state.set(DaemonState.IDLE)
    }
}