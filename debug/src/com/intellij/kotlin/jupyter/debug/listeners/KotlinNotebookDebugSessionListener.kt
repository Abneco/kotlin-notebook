// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.listeners

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.util.messages.Topic

@Topic.ProjectLevel
internal val NOTEBOOK_DEBUG_SESSION_TOPIC: Topic<KotlinNotebookDebugSessionListener> =
    Topic(KotlinNotebookDebugSessionListener::class.java, Topic.BroadcastDirection.NONE, true)

/**
 * Listener for notebook debug session lifecycle events.
 */
interface KotlinNotebookDebugSessionListener {
    /**
     * This happens after the debugger attaches and the kernel thread breakpoint is hit,
     * indicating that the session is ready for debugging operations.
     */
    fun onSessionInitialized(notebookFile: BackedNotebookVirtualFile) {}

    /**
     * This indicates that the port is released and a new session can be created.
     */
    fun onProcessDetached(notebookFile: BackedNotebookVirtualFile) {}
}
