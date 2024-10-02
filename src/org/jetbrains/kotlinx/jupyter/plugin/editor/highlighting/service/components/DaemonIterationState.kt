// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.components

import java.util.concurrent.atomic.AtomicReference

internal class DaemonIterationState {
    companion object {
        enum class DaemonState {
            IDLE,
            SETUP,
            IN_PROGRESS,
        }
    }
    private val state = AtomicReference(DaemonState.IDLE)

    fun get(): DaemonState = state.get()

    val isIdle get() = state.get() == DaemonState.IDLE
    val isInProgress get() = state.get() == DaemonState.IN_PROGRESS

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