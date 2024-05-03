// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util

import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingManager.Companion.DaemonState
import java.util.concurrent.atomic.AtomicReference

internal class IterationState {
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

    fun enterIdlePhase(): Boolean {
        return state.compareAndSet(DaemonState.IN_PROGRESS, DaemonState.IDLE)
    }

    fun reset() {
        state.set(DaemonState.IDLE)
    }
}