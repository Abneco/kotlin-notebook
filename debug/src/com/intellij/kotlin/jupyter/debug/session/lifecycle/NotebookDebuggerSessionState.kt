// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.session.lifecycle

/**
 * Represents the current state of the notebook debug session.
 *
 * @see [com.intellij.kotlin.jupyter.debug.session.KotlinNotebookFileDebugSession]
 */
internal enum class NotebookDebuggerSessionState {
    /** No active debug session */
    Absent,
    /** Session is being created, waiting for internal instrumentation */
    Initializing,
    /** Session is ready, all instrumentation is done */
    Ready,
    /** Session is being disposed */
    Disposing
}
