// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state

import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.DaemonState.IDLE
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.DaemonState.IN_PROGRESS
import com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state.DaemonState.SETUP
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents the states of an analyzer daemon lifecycle during the highlighting pass.
 *
 * - [IDLE]: Indicates that the daemon is completed and no actions are being performed.
 * - [SETUP]: Indicates that the daemon is in the setup phase, HL pass
 * gathers info about ranges to highlight.
 * - [IN_PROGRESS]: Indicates that the daemon is performing the pass.
 *
 * [isIdle] Returns `true` if the current state is [IDLE], otherwise `false`.
 * [isInProgress] Returns `true` if the current state is [IN_PROGRESS], otherwise `false`.
 */
internal enum class DaemonState {
    IDLE,
    SETUP,
    IN_PROGRESS;

    val isIdle: Boolean get() = this == IDLE
    val isInProgress: Boolean get() = this == IN_PROGRESS
}

internal class DaemonIterationState {
    private val state = AtomicReference(IDLE)

    fun get(): DaemonState = state.get()

    val isIdle: Boolean get() = get().isIdle
    val isInProgress: Boolean get() = get().isInProgress

    fun enterSetupPhase(): Boolean {
        return state.compareAndSet(IDLE, SETUP)
    }

    fun enterProgressPhase(): Boolean {
        return state.compareAndSet(SETUP, IN_PROGRESS)
    }

    fun enterIdlePhase(): Boolean {
        return state.compareAndSet(IN_PROGRESS,   IDLE)
    }

    fun setIdle() {
        state.set(IDLE)
    }
}